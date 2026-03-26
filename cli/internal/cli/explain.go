package cli

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"

	"github.com/spf13/cobra"
)

type explainCorrelation struct {
	DeviceModel string `json:"device_model"`
	OSVersion   string `json:"os_version"`
	Count       int    `json:"count"`
}

type explainReport struct {
	OK               bool                 `json:"ok"`
	ErrorID          string               `json:"error_id"`
	Event            map[string]any       `json:"event,omitempty"`
	SimilarCount     int                  `json:"similar_count"`
	Correlations     []explainCorrelation `json:"correlations,omitempty"`
	Suggestion       string               `json:"suggestion,omitempty"`
	IncludeThrowable bool                 `json:"include_throwable"`
}

func newExplainCommand() *cobra.Command {
	var includeThrowable bool

	cmd := &cobra.Command{
		Use:   "explain <error-id>",
		Short: "Explain an error event: context, similar occurrences, and device correlation",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			errorID := strings.TrimSpace(args[0])
			if errorID == "" {
				return newUsageError("error-id must not be empty")
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			selectCols := logsSelectColumns
			if includeThrowable {
				selectCols = logsSelectColumnsWithThrowable
			}

			// Fetch the specific event by id
			ctx1, cancel1 := context.WithTimeout(context.Background(), cfg.timeout())
			defer cancel1()
			event, err := fetchEventByID(ctx1, cfg, errorID, selectCols)
			if err != nil {
				return fmt.Errorf("failed to fetch event %q: %w", errorID, err)
			}
			if event == nil {
				return fmt.Errorf("event %q not found", errorID)
			}

			// Query similar events (same level + tag + message prefix)
			level, _ := event["level"].(string)
			tag, _ := event["tag"].(string)
			message, _ := event["message"].(string)
			prefix := messagePrefix(message, 50)

			similarReq := telemetryQueryRequest{
				Source:   "logs",
				Severity: strings.ToLower(level),
				Tag:      tag,
				Contains: prefix,
				Limit:    100,
				Order:    "desc",
			}
			ctx2, cancel2 := context.WithTimeout(context.Background(), cfg.timeout())
			defer cancel2()
			similarRows, similarErr := doQueryWithRetry(ctx2, cfg, similarReq, 3)
			if similarErr != nil {
				similarRows = nil
			}

			// Build device model correlations
			correlationMap := map[string]*explainCorrelation{}
			for _, row := range similarRows {
				di, _ := row["device_info"].(map[string]any)
				var model, osVer string
				if di != nil {
					model, _ = di["model"].(string)
					osVer, _ = di["os_version"].(string)
				}
				key := model + "|" + osVer
				if c, ok := correlationMap[key]; ok {
					c.Count++
				} else {
					correlationMap[key] = &explainCorrelation{DeviceModel: model, OSVersion: osVer, Count: 1}
				}
			}
			correlations := make([]explainCorrelation, 0, len(correlationMap))
			for _, c := range correlationMap {
				correlations = append(correlations, *c)
			}
			sortCorrelationsByCount(correlations)

			suggestion := buildExplainSuggestion(level, tag, len(similarRows), correlations)

			report := explainReport{
				OK:               true,
				ErrorID:          errorID,
				Event:            event,
				SimilarCount:     len(similarRows),
				Correlations:     correlations,
				Suggestion:       suggestion,
				IncludeThrowable: includeThrowable,
			}

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), report)
			}
			return printExplainReport(cmd.OutOrStdout(), report)
		},
	}

	cmd.Flags().BoolVar(&includeThrowable, "throwable", true, "Include stack trace in event details")
	return cmd
}

// fetchEventByID queries app_logs filtering by primary key id.
func fetchEventByID(ctx context.Context, cfg supabaseConfig, id, selectCols string) (map[string]any, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return nil, fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", cfg.LogsTable)
	q := base.Query()
	q.Set("select", selectCols)
	q.Set("id", "eq."+id)
	q.Set("limit", "1")
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
	if len(rows) == 0 {
		return nil, nil
	}
	return rows[0], nil
}

func messagePrefix(msg string, n int) string {
	msg = strings.TrimSpace(msg)
	if len(msg) <= n {
		return msg
	}
	return msg[:n]
}

func sortCorrelationsByCount(c []explainCorrelation) {
	for i := 1; i < len(c); i++ {
		for j := i; j > 0 && c[j].Count > c[j-1].Count; j-- {
			c[j], c[j-1] = c[j-1], c[j]
		}
	}
}

func buildExplainSuggestion(level, tag string, similarCount int, correlations []explainCorrelation) string {
	if similarCount == 0 {
		return "This appears to be an isolated incident with no similar events in the queried range."
	}
	if len(correlations) > 0 && correlations[0].Count > similarCount/2 {
		top := correlations[0]
		return fmt.Sprintf(
			"High recurrence (%d similar events). Most affected: %s / %s (%d occurrences). Consider a device-specific fix or targeted canary rollout.",
			similarCount, top.DeviceModel, top.OSVersion, top.Count,
		)
	}
	return fmt.Sprintf("%d similar %s events found for tag %q — investigate as a systemic issue.", similarCount, strings.ToLower(level), tag)
}

func printExplainReport(out io.Writer, r explainReport) error {
	_, err := fmt.Fprintf(out,
		"error_id: %s\nsimilar_count: %d\nsuggestion: %s\n",
		r.ErrorID, r.SimilarCount, r.Suggestion,
	)
	return err
}
