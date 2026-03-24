package cli

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

type batchVerifyStatus string

const (
	batchStatusOK         batchVerifyStatus = "OK"
	batchStatusIncomplete batchVerifyStatus = "INCOMPLETE"
	batchStatusNoHash     batchVerifyStatus = "NO_HASH"
)

type batchVerifyResult struct {
	BatchID       string            `json:"batch_id"`
	ExpectedCount int               `json:"expected_count"`
	ActualCount   int               `json:"actual_count"`
	Hash          string            `json:"hash,omitempty"`
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
	Results      []batchVerifyResult `json:"results"`
}

func newVerifyCommand() *cobra.Command {
	var fromFlag string
	var toFlag string
	var environment string

	cmd := &cobra.Command{
		Use:   "verify",
		Short: "Verify batch integrity against the log_batches manifest table",
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

			// Fetch batch manifests in range
			batches, err := fetchBatchManifests(ctx, cfg, from, to, environment)
			if err != nil {
				return fmt.Errorf("failed to fetch batch manifests: %w", err)
			}

			results := make([]batchVerifyResult, 0, len(batches))
			okCount, incompleteCount, noHashCount := 0, 0, 0

			for _, batch := range batches {
				batchID, _ := batch["batch_id"].(string)
				expectedCountF, _ := batch["event_count"].(float64)
				expectedCount := int(expectedCountF)
				hash, _ := batch["batch_hash"].(string)

				// Count actual events for this batch_id
				actualCount, err := countEventsByBatchID(ctx, cfg, batchID)
				if err != nil {
					actualCount = -1
				}

				var status batchVerifyStatus
				switch {
				case strings.TrimSpace(hash) == "":
					status = batchStatusNoHash
					noHashCount++
				case actualCount == expectedCount:
					status = batchStatusOK
					okCount++
				default:
					status = batchStatusIncomplete
					incompleteCount++
				}

				results = append(results, batchVerifyResult{
					BatchID:       batchID,
					ExpectedCount: expectedCount,
					ActualCount:   actualCount,
					Hash:          hash,
					Status:        status,
				})
			}

			report := verifyReport{
				OK:           true,
				From:         from,
				To:           to,
				Environment:  environment,
				TotalBatches: len(batches),
				OKCount:      okCount,
				Incomplete:   incompleteCount,
				NoHash:       noHashCount,
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
	return cmd
}

func fetchBatchManifests(ctx context.Context, cfg supabaseConfig, from, to, environment string) ([]map[string]any, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return nil, fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", "log_batches")
	q := base.Query()
	q.Set("select", "batch_id,event_count,batch_hash,sent_at,environment")
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

func countEventsByBatchID(ctx context.Context, cfg supabaseConfig, batchID string) (int, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return 0, err
	}
	base.Path = path.Join(base.Path, "rest", "v1", cfg.LogsTable)
	q := base.Query()
	q.Set("select", "id")
	q.Set("batch_id", "eq."+batchID)
	q.Set("limit", "1000")
	base.RawQuery = q.Encode()

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

	client := supabaseHTTPClient(cfg)
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
		return 0, fmt.Errorf("status %d", resp.StatusCode)
	}

	rows := make([]map[string]any, 0)
	if len(strings.TrimSpace(string(body))) > 0 {
		if err := decodeJSONBytes(body, &rows); err != nil {
			return 0, err
		}
	}
	return len(rows), nil
}

func printVerifyReport(out io.Writer, r verifyReport) error {
	_, err := fmt.Fprintf(out,
		"verify:\n  from: %s\n  to: %s\n  total_batches: %d\n  ok: %d\n  incomplete: %d\n  no_hash: %d\n",
		r.From, r.To, r.TotalBatches, r.OKCount, r.Incomplete, r.NoHash,
	)
	if err != nil {
		return err
	}
	for _, res := range r.Results {
		if res.Status != batchStatusOK {
			if _, e := fmt.Fprintf(out, "  [%s] batch=%s expected=%d actual=%d\n",
				res.Status, res.BatchID, res.ExpectedCount, res.ActualCount); e != nil {
				return e
			}
		}
	}
	return nil
}
