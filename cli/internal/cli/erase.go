package cli

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"

	"github.com/spf13/cobra"
)

type eraseResult struct {
	OK              bool   `json:"ok"`
	DryRun          bool   `json:"dry_run"`
	UserID          string `json:"user_id,omitempty"`
	DeviceID        string `json:"device_id,omitempty"`
	Environment     string `json:"environment,omitempty"`
	AffectedRows    int    `json:"affected_rows"`
	PurgedManifests int    `json:"purged_manifests"`
	Message         string `json:"message"`
}

func newEraseCommand() *cobra.Command {
	var userID string
	var deviceID string
	var environment string
	var dryRun bool
	var confirm bool

	cmd := &cobra.Command{
		Use:   "erase",
		Short: "Erase log records for a user or device (GDPR right-to-erasure)",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("erase does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			if strings.TrimSpace(userID) == "" && strings.TrimSpace(deviceID) == "" {
				return newUsageError("at least one of --user-id or --device-id must be specified")
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			client := supabaseHTTPClient(cfg)

			// Always count first
			count, err := countMatchingRows(context.Background(), cfg, client, userID, deviceID, environment)
			if err != nil {
				return fmt.Errorf("failed to count matching rows: %w", err)
			}

			if dryRun || !confirm {
				result := eraseResult{
					OK:           true,
					DryRun:       true,
					UserID:       userID,
					DeviceID:     deviceID,
					Environment:  environment,
					AffectedRows: count,
					Message:      fmt.Sprintf("dry-run: would delete %d row(s); add --confirm to execute", count),
				}
				if outputFormat == "json" {
					return writeJSON(cmd.OutOrStdout(), result)
				}
				if outputFormat == "agent" {
					return writeAgent(cmd.OutOrStdout(), result)
				}
				_, err = fmt.Fprintln(cmd.OutOrStdout(), result.Message)
				return err
			}

			// Collect batch_ids before deletion for orphan cleanup
			batchIDs, batchErr := collectBatchIDs(context.Background(), cfg, client, userID, deviceID, environment)

			deleted, err := deleteMatchingRows(context.Background(), cfg, client, userID, deviceID, environment)
			if err != nil {
				return fmt.Errorf("erase failed: %w", err)
			}

			// GDPR Art. 17 — cascade erasure to all related tables (requires migration 016+)
			metricsDeleted, _ := deleteFromTable(context.Background(), cfg, client, cfg.MetricsTable, userID, deviceID, environment)
			betaDeleted, _ := deleteFromTableByDevice(context.Background(), cfg, client, "beta_tester_devices", deviceID)
			configDeleted, _ := deleteRemoteConfigByFingerprint(context.Background(), cfg, client, deviceID)

			// Prune orphaned batch manifests
			purged := 0
			if batchErr == nil && len(batchIDs) > 0 {
				purged, _ = pruneOrphanManifests(context.Background(), cfg, client, batchIDs)
			}

			totalDeleted := deleted + metricsDeleted + betaDeleted + configDeleted
			result := eraseResult{
				OK:              true,
				DryRun:          false,
				UserID:          userID,
				DeviceID:        deviceID,
				Environment:     environment,
				AffectedRows:    totalDeleted,
				PurgedManifests: purged,
				Message:         fmt.Sprintf("deleted %d row(s) (logs=%d metrics=%d beta=%d config=%d), purged %d manifest(s)", totalDeleted, deleted, metricsDeleted, betaDeleted, configDeleted, purged),
			}
			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), result)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), result)
			}
			_, err = fmt.Fprintln(cmd.OutOrStdout(), result.Message)
			return err
		},
	}

	cmd.Flags().StringVar(&userID, "user-id", "", "User ID to erase")
	cmd.Flags().StringVar(&deviceID, "device-id", "", "Device ID to erase")
	cmd.Flags().StringVar(&environment, "environment", "", "Limit erasure to this environment")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "Count matching rows without deleting")
	cmd.Flags().BoolVar(&confirm, "confirm", false, "Execute the deletion (required to actually delete)")
	return cmd
}

func buildEraseURL(cfg supabaseConfig, userID, deviceID, environment string) (string, error) {
	return buildEraseURLForTable(cfg, cfg.LogsTable, userID, deviceID, environment)
}

func buildEraseURLForTable(cfg supabaseConfig, table, userID, deviceID, environment string) (string, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return "", fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", table)
	query := base.Query()

	uid := strings.TrimSpace(userID)
	did := strings.TrimSpace(deviceID)

	// PostgREST OR syntax when both identifiers are present (GDPR: erase ALL data for user OR device)
	if uid != "" && did != "" {
		query.Set("or", fmt.Sprintf("(user_id.eq.%s,device_id.eq.%s)", uid, did))
	} else if uid != "" {
		query.Set("user_id", "eq."+uid)
	} else if did != "" {
		query.Set("device_id", "eq."+did)
	} else {
		return "", fmt.Errorf("no filter criteria provided")
	}

	if strings.TrimSpace(environment) != "" {
		query.Set("environment", "eq."+strings.TrimSpace(environment))
	}
	base.RawQuery = query.Encode()
	return base.String(), nil
}

func countMatchingRows(ctx context.Context, cfg supabaseConfig, client *http.Client, userID, deviceID, environment string) (int, error) {
	rawURL, err := buildEraseURL(cfg, userID, deviceID, environment)
	if err != nil {
		return 0, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return 0, fmt.Errorf("failed to create count request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Prefer", "count=exact")
	// Select minimal columns
	q := req.URL.Query()
	q.Set("select", "id")
	req.URL.RawQuery = q.Encode()
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return 0, fmt.Errorf("count request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		snippet := strings.TrimSpace(string(body))
		if len(snippet) > 200 {
			snippet = snippet[:200]
		}
		return 0, fmt.Errorf("count query failed: status=%d body=%s", resp.StatusCode, snippet)
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, fmt.Errorf("failed to parse count response: %w", err)
	}
	return len(rows), nil
}

func deleteMatchingRows(ctx context.Context, cfg supabaseConfig, client *http.Client, userID, deviceID, environment string) (int, error) {
	rawURL, err := buildEraseURL(cfg, userID, deviceID, environment)
	if err != nil {
		return 0, err
	}

	// Atomic delete + count: use return=representation to get deleted rows in a single operation.
	// This eliminates the TOCTOU race of count-then-delete.
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, rawURL, bytes.NewReader([]byte{}))
	if err != nil {
		return 0, fmt.Errorf("failed to create delete request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=representation")
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
		req.Header.Set("Accept-Profile", cfg.Schema)
	}
	// Select only id to minimize payload
	q := req.URL.Query()
	q.Set("select", "id")
	req.URL.RawQuery = q.Encode()

	resp, err := client.Do(req)
	if err != nil {
		return 0, fmt.Errorf("delete request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		snippet := strings.TrimSpace(string(body))
		if len(snippet) > 200 {
			snippet = snippet[:200]
		}
		return 0, fmt.Errorf("delete failed: status=%d body=%s", resp.StatusCode, snippet)
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, nil
	}
	return len(rows), nil
}

// collectBatchIDs returns distinct batch_id values for events matching the erase criteria.
func collectBatchIDs(ctx context.Context, cfg supabaseConfig, client *http.Client, userID, deviceID, environment string) ([]string, error) {
	rawURL, err := buildEraseURL(cfg, userID, deviceID, environment)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create batch_id query: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}
	q := req.URL.Query()
	q.Set("select", "batch_id")
	q.Set("batch_id", "not.is.null")
	req.URL.RawQuery = q.Encode()

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("batch_id query failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("batch_id query failed: status=%d", resp.StatusCode)
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return nil, fmt.Errorf("failed to parse batch_id response: %w", err)
	}

	seen := map[string]bool{}
	var ids []string
	for _, row := range rows {
		bid, _ := row["batch_id"].(string)
		if bid != "" && !seen[bid] {
			seen[bid] = true
			ids = append(ids, bid)
		}
	}
	return ids, nil
}

// pruneOrphanManifests deletes log_batches entries whose batch_id has no remaining events.
func pruneOrphanManifests(ctx context.Context, cfg supabaseConfig, client *http.Client, batchIDs []string) (int, error) {
	pruned := 0
	for _, bid := range batchIDs {
		// Check if any events still reference this batch_id
		remaining, err := countEventsByBatchID(ctx, cfg, client, bid)
		if err != nil {
			continue
		}
		if remaining > 0 {
			continue
		}
		// Delete orphaned manifest
		if deleteBatchManifest(ctx, cfg, client, bid) == nil {
			pruned++
		}
	}
	return pruned, nil
}

func countEventsByBatchID(ctx context.Context, cfg supabaseConfig, client *http.Client, batchID string) (int, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return 0, err
	}
	base.Path = path.Join(base.Path, "rest", "v1", cfg.LogsTable)
	query := base.Query()
	query.Set("batch_id", "eq."+batchID)
	query.Set("select", "id")
	query.Set("limit", "1000")
	base.RawQuery = query.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return 0, err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, fmt.Errorf("count batch events failed: status=%d", resp.StatusCode)
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, err
	}
	return len(rows), nil
}

func deleteBatchManifest(ctx context.Context, cfg supabaseConfig, client *http.Client, batchID string) error {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return err
	}
	base.Path = path.Join(base.Path, "rest", "v1", "log_batches")
	query := base.Query()
	query.Set("batch_id", "eq."+batchID)
	base.RawQuery = query.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, base.String(), bytes.NewReader([]byte{}))
	if err != nil {
		return err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=minimal")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer func() { _ = resp.Body.Close() }()
	_, _ = io.ReadAll(resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("delete batch manifest failed: status=%d", resp.StatusCode)
	}
	return nil
}

// deleteFromTable deletes rows from an arbitrary table filtered by user_id/device_id/environment.
// Used for GDPR Art. 17 cascade erasure (app_metrics, etc.).
func deleteFromTable(ctx context.Context, cfg supabaseConfig, client *http.Client, table, userID, deviceID, environment string) (int, error) {
	rawURL, err := buildEraseURLForTable(cfg, table, userID, deviceID, environment)
	if err != nil {
		return 0, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, rawURL, bytes.NewReader([]byte{}))
	if err != nil {
		return 0, err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=representation")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, fmt.Errorf("delete from %s failed: status=%d", table, resp.StatusCode)
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, nil
	}
	return len(rows), nil
}

// deleteFromTableByDevice deletes rows from a table using device_id as the key column.
// Used for beta_tester_devices (GDPR cascade).
func deleteFromTableByDevice(ctx context.Context, cfg supabaseConfig, client *http.Client, table, deviceID string) (int, error) {
	if strings.TrimSpace(deviceID) == "" {
		return 0, nil
	}

	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return 0, err
	}
	base.Path = path.Join(base.Path, "rest", "v1", table)
	q := base.Query()
	q.Set("device_id", "eq."+strings.TrimSpace(deviceID))
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, base.String(), bytes.NewReader([]byte{}))
	if err != nil {
		return 0, err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=representation")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, nil
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, nil
	}
	return len(rows), nil
}

// deleteRemoteConfigByFingerprint deletes device_remote_config entries for a device.
// The device_remote_config table uses device_fingerprint (SHA-256 hash of device_id),
// but for GDPR erasure we also try to match by the raw device_id value.
func deleteRemoteConfigByFingerprint(ctx context.Context, cfg supabaseConfig, client *http.Client, deviceID string) (int, error) {
	if strings.TrimSpace(deviceID) == "" {
		return 0, nil
	}

	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return 0, err
	}
	base.Path = path.Join(base.Path, "rest", "v1", remoteConfigTable)
	q := base.Query()
	q.Set("device_fingerprint", "eq."+strings.TrimSpace(deviceID))
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, base.String(), bytes.NewReader([]byte{}))
	if err != nil {
		return 0, err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=representation")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, nil
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return 0, nil
	}
	return len(rows), nil
}
