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
	OK          bool   `json:"ok"`
	DryRun      bool   `json:"dry_run"`
	UserID      string `json:"user_id,omitempty"`
	DeviceID    string `json:"device_id,omitempty"`
	Environment string `json:"environment,omitempty"`
	AffectedRows int   `json:"affected_rows"`
	Message     string `json:"message"`
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

			deleted, err := deleteMatchingRows(context.Background(), cfg, client, userID, deviceID, environment)
			if err != nil {
				return fmt.Errorf("erase failed: %w", err)
			}

			result := eraseResult{
				OK:           true,
				DryRun:       false,
				UserID:       userID,
				DeviceID:     deviceID,
				Environment:  environment,
				AffectedRows: deleted,
				Message:      fmt.Sprintf("deleted %d row(s)", deleted),
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
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return "", fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", cfg.LogsTable)
	query := base.Query()

	filters := 0
	if strings.TrimSpace(userID) != "" {
		query.Set("user_id", "eq."+strings.TrimSpace(userID))
		filters++
	}
	if strings.TrimSpace(deviceID) != "" {
		query.Set("device_id", "eq."+strings.TrimSpace(deviceID))
		filters++
	}
	if strings.TrimSpace(environment) != "" {
		query.Set("environment", "eq."+strings.TrimSpace(environment))
	}
	if filters == 0 {
		return "", fmt.Errorf("no filter criteria provided")
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

	// First count (to return deleted count, since PostgREST DELETE with return=representation is heavy)
	count, err := countMatchingRows(ctx, cfg, client, userID, deviceID, environment)
	if err != nil {
		return 0, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, rawURL, bytes.NewReader([]byte{}))
	if err != nil {
		return 0, fmt.Errorf("failed to create delete request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=minimal")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
	}

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
	return count, nil
}
