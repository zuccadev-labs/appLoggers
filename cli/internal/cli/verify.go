package cli

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

type batchVerifyStatus string

const (
	batchStatusOK         batchVerifyStatus = "OK"
	batchStatusIncomplete batchVerifyStatus = "INCOMPLETE"
	batchStatusNoHash     batchVerifyStatus = "NO_HASH"
	batchStatusTampered   batchVerifyStatus = "TAMPERED"
	batchStatusNoSecret   batchVerifyStatus = "NO_SECRET"
)

type batchVerifyResult struct {
	BatchID       string            `json:"batch_id"`
	Signal        string            `json:"signal"`
	ExpectedCount int               `json:"expected_count"`
	ActualCount   int               `json:"actual_count"`
	Hash          string            `json:"hash,omitempty"`
	KeyID         string            `json:"key_id,omitempty"`
	Status        batchVerifyStatus `json:"status"`
}

type verifyReport struct {
	OK           bool                `json:"ok"`
	From         string              `json:"from"`
	To           string              `json:"to"`
	Environment  string              `json:"environment,omitempty"`
	TotalBatches int                 `json:"total_batches"`
	OKCount      int                 `json:"ok_count"`
	Incomplete   int                 `json:"incomplete_count"`
	NoHash       int                 `json:"no_hash_count"`
	NoSecret     int                 `json:"no_secret_count"`
	Tampered     int                 `json:"tampered_count"`
	Results      []batchVerifyResult `json:"results"`
}

func newVerifyCommand() *cobra.Command {
	var fromFlag string
	var toFlag string
	var environment string
	var signal string

	cmd := &cobra.Command{
		Use:   "verify",
		Short: "Verify batch integrity against log_batches and metric_batches",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("verify does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			now := time.Now().UTC()
			from := now.Add(-24 * time.Hour).Format(time.RFC3339)
			to := now.Format(time.RFC3339)

			if fromFlag != "" {
				if _, err := time.Parse(time.RFC3339, fromFlag); err != nil {
					return newUsageError("invalid --from value %q (expected RFC3339)", fromFlag)
				}
				from = fromFlag
			}
			if toFlag != "" {
				if _, err := time.Parse(time.RFC3339, toFlag); err != nil {
					return newUsageError("invalid --to value %q (expected RFC3339)", toFlag)
				}
				to = toFlag
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			ctx, cancel := context.WithTimeout(context.Background(), cfg.timeout())
			defer cancel()

			client := supabaseHTTPClient(cfg)

			selectedSignals, err := parseVerifySignals(signal)
			if err != nil {
				return err
			}

			results := make([]batchVerifyResult, 0)
			okCount, incompleteCount, noHashCount, noSecretCount, tamperedCount := 0, 0, 0, 0, 0

			for _, currentSignal := range selectedSignals {
				batches, err := fetchBatchManifests(ctx, cfg, from, to, environment, currentSignal)
				if err != nil {
					return fmt.Errorf("failed to fetch %s batch manifests: %w", currentSignal, err)
				}

				for _, batch := range batches {
					batchID, _ := batch["batch_id"].(string)
					expectedCountF, _ := batch["event_count"].(float64)
					expectedCount := int(expectedCountF)
					hash, _ := batch["batch_hash"].(string)
					keyID, _ := batch["key_id"].(string)

					actualCount, countErr := countEventsByBatchIDForSignal(ctx, cfg, client, batchID, currentSignal)
					if countErr != nil {
						actualCount = -1
					}

					secret := cfg.integritySecretForKey(keyID)
					var status batchVerifyStatus
					switch {
					case strings.TrimSpace(hash) == "":
						status = batchStatusNoHash
						noHashCount++
					case actualCount != expectedCount:
						status = batchStatusIncomplete
						incompleteCount++
					case secret == "":
						status = batchStatusNoSecret
						noSecretCount++
					default:
						computedHash, hashErr := computeBatchHMAC(ctx, cfg, client, batchID, secret, currentSignal)
						if hashErr != nil {
							status = batchStatusIncomplete
							incompleteCount++
						} else if subtle.ConstantTimeCompare([]byte(computedHash), []byte(hash)) == 1 {
							status = batchStatusOK
							okCount++
						} else {
							status = batchStatusTampered
							tamperedCount++
						}
					}

					results = append(results, batchVerifyResult{
						BatchID:       batchID,
						Signal:        currentSignal,
						ExpectedCount: expectedCount,
						ActualCount:   actualCount,
						Hash:          hash,
						KeyID:         keyID,
						Status:        status,
					})
				}
			}

			report := verifyReport{
				OK:           tamperedCount == 0 && incompleteCount == 0,
				From:         from,
				To:           to,
				Environment:  environment,
				TotalBatches: len(results),
				OKCount:      okCount,
				Incomplete:   incompleteCount,
				NoHash:       noHashCount,
				NoSecret:     noSecretCount,
				Tampered:     tamperedCount,
				Results:      results,
			}

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), report)
			}
			return printVerifyReport(cmd.OutOrStdout(), report)
		},
	}

	cmd.Flags().StringVar(&fromFlag, "from", "", "Start time (RFC3339); default: 24h ago")
	cmd.Flags().StringVar(&toFlag, "to", "", "End time (RFC3339); default: now")
	cmd.Flags().StringVar(&environment, "environment", "", "Filter by environment")
	cmd.Flags().StringVar(&signal, "signal", "both", "Signal to verify: logs, metrics, or both")
	return cmd
}

func fetchBatchManifests(ctx context.Context, cfg supabaseConfig, from, to, environment, signal string) ([]map[string]any, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return nil, fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", batchManifestTable(signal))
	q := base.Query()
	q.Set("select", "batch_id,event_count,batch_hash,sent_at,environment,key_id")
	q.Set("order", "sent_at.desc")
	q.Set("limit", "500")
	if from != "" {
		q.Add("sent_at", "gte."+from)
	}
	if to != "" {
		q.Add("sent_at", "lte."+to)
	}
	if environment != "" {
		q.Set("environment", "eq."+environment)
	}
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	client := supabaseHTTPClient(cfg)
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		_, _, httpErr := classifyHTTPError(resp.StatusCode, body, resp.Header)
		return nil, httpErr
	}

	rows := make([]map[string]any, 0)
	if len(strings.TrimSpace(string(body))) > 0 {
		if err := decodeJSONBytes(body, &rows); err != nil {
			return nil, fmt.Errorf("failed to parse response: %w", err)
		}
	}
	return rows, nil
}

// computeBatchHMAC fetches all events for a batch_id, sorts by id, builds the canonical
// string and computes HMAC-SHA256 — mirroring the SDK's BatchIntegrityManager.computeHash().
//
// Canonical format: events sorted by id, joined with "|":
//
//	"${id}:${timestamp}:${level}:${tag}:${message[:200]}"
func computeBatchHMAC(ctx context.Context, cfg supabaseConfig, client *http.Client, batchID, secret, signal string) (string, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return "", fmt.Errorf("invalid URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", batchEventsTable(cfg, signal))
	q := base.Query()
	q.Set("select", batchSelectFields(signal))
	q.Set("batch_id", "eq."+batchID)
	q.Set("order", "id.asc")
	q.Set("limit", "100000")
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer func() { _ = resp.Body.Close() }()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("HTTP %d fetching batch events", resp.StatusCode)
	}

	var events []map[string]any
	if err := decodeJSONBytes(body, &events); err != nil {
		return "", err
	}

	// Sort by id (should already be sorted, but enforce for correctness)
	sort.Slice(events, func(i, j int) bool {
		idI, _ := events[i]["id"].(string)
		idJ, _ := events[j]["id"].(string)
		return idI < idJ
	})

	// Build canonical string matching SDK.
	parts := make([]string, 0, len(events))
	for _, evt := range events {
		id, _ := evt["id"].(string)
		ts := fmt.Sprintf("%d", int64(numberField(evt, "timestamp")))
		if signal == "metrics" {
			name, _ := evt["name"].(string)
			unit, _ := evt["unit"].(string)
			value := canonicalMetricValue(numberField(evt, "value"))
			parts = append(parts, fmt.Sprintf("%s:%s:%s:%s:%s", id, ts, name, value, unit))
			continue
		}
		level, _ := evt["level"].(string)
		tag, _ := evt["tag"].(string)
		msg, _ := evt["message"].(string)
		if runes := []rune(msg); len(runes) > 200 {
			msg = string(runes[:200])
		}
		parts = append(parts, fmt.Sprintf("%s:%s:%s:%s:%s", id, ts, level, tag, msg))
	}
	canonical := strings.Join(parts, "|")

	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(canonical))
	return hex.EncodeToString(mac.Sum(nil)), nil
}

func printVerifyReport(out io.Writer, r verifyReport) error {
	_, err := fmt.Fprintf(out,
		"verify:\n  from: %s\n  to: %s\n  total_batches: %d\n  ok: %d\n  incomplete: %d\n  no_hash: %d\n  no_secret: %d\n  tampered: %d\n",
		r.From, r.To, r.TotalBatches, r.OKCount, r.Incomplete, r.NoHash, r.NoSecret, r.Tampered,
	)
	if err != nil {
		return err
	}
	for _, res := range r.Results {
		if res.Status != batchStatusOK && res.Status != batchStatusNoSecret {
			if _, e := fmt.Fprintf(out, "  [%s] signal=%s batch=%s key_id=%s expected=%d actual=%d\n",
				res.Status, res.Signal, res.BatchID, res.KeyID, res.ExpectedCount, res.ActualCount); e != nil {
				return e
			}
		}
	}
	return nil
}

func parseVerifySignals(signal string) ([]string, error) {
	switch strings.ToLower(strings.TrimSpace(signal)) {
	case "", "both":
		return []string{"logs", "metrics"}, nil
	case "logs", "metrics":
		return []string{strings.ToLower(strings.TrimSpace(signal))}, nil
	default:
		return nil, newUsageError("invalid --signal value %q (expected logs, metrics, or both)", signal)
	}
}

func batchManifestTable(signal string) string {
	if signal == "metrics" {
		return "metric_batches"
	}
	return "log_batches"
}

func batchEventsTable(cfg supabaseConfig, signal string) string {
	if signal == "metrics" {
		return cfg.MetricsTable
	}
	return cfg.LogsTable
}

func batchSelectFields(signal string) string {
	if signal == "metrics" {
		return "id,timestamp,name,value,unit"
	}
	return "id,timestamp,level,tag,message"
}

func countEventsByBatchIDForSignal(ctx context.Context, cfg supabaseConfig, client *http.Client, batchID, signal string) (int, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return 0, err
	}
	base.Path = path.Join(base.Path, "rest", "v1", batchEventsTable(cfg, signal))
	query := base.Query()
	query.Set("batch_id", "eq."+batchID)
	query.Set("select", "id")
	query.Set("limit", "100000")
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

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return 0, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, fmt.Errorf("count batch events failed: status=%d", resp.StatusCode)
	}

	var rows []map[string]any
	if err := decodeJSONBytes(body, &rows); err != nil {
		return 0, err
	}
	return len(rows), nil
}

func numberField(row map[string]any, field string) float64 {
	value, ok := row[field]
	if !ok || value == nil {
		return 0
	}
	if floatValue, ok := value.(float64); ok {
		return floatValue
	}
	if stringValue, ok := value.(string); ok {
		parsed, err := strconv.ParseFloat(strings.TrimSpace(stringValue), 64)
		if err == nil {
			return parsed
		}
	}
	return 0
}

func canonicalMetricValue(value float64) string {
	if value == float64(int64(value)) {
		return fmt.Sprintf("%d.0", int64(value))
	}
	return strconv.FormatFloat(value, 'f', -1, 64)
}
