package cli

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strconv"
	"strings"
	"time"
)

// telemetryQueryRequest holds all filter and pagination parameters for a telemetry query.
type telemetryQueryRequest struct {
	Source      string `json:"source" toon:"source"`
	Aggregate   string `json:"aggregate,omitempty" toon:"aggregate,omitempty"`
	From        string `json:"from,omitempty" toon:"from,omitempty"`
	To          string `json:"to,omitempty" toon:"to,omitempty"`
	Severity    string `json:"severity,omitempty" toon:"severity,omitempty"`
	MinSeverity string `json:"min_severity,omitempty" toon:"min_severity,omitempty"`
	Environment string `json:"environment,omitempty" toon:"environment,omitempty"`
	SessionID   string `json:"session_id,omitempty" toon:"session_id,omitempty"`
	DeviceID    string `json:"device_id,omitempty" toon:"device_id,omitempty"`
	Fingerprint string `json:"fingerprint,omitempty" toon:"fingerprint,omitempty"`
	UserID      string `json:"user_id,omitempty" toon:"user_id,omitempty"`
	Package     string `json:"package,omitempty" toon:"package,omitempty"`
	ErrorCode   string `json:"error_code,omitempty" toon:"error_code,omitempty"`
	Contains    string `json:"contains,omitempty" toon:"contains,omitempty"`
	Tag         string `json:"tag,omitempty" toon:"tag,omitempty"`
	Name        string `json:"name,omitempty" toon:"name,omitempty"`
	AnomalyType string `json:"anomaly_type,omitempty" toon:"anomaly_type,omitempty"`
	TraceID     string `json:"trace_id,omitempty" toon:"trace_id,omitempty"`
	Variant     string `json:"variant,omitempty" toon:"variant,omitempty"`
	BatchID     string `json:"batch_id,omitempty" toon:"batch_id,omitempty"`
	ExtraKey    string `json:"extra_key,omitempty" toon:"extra_key,omitempty"`
	ExtraValue  string `json:"extra_value,omitempty" toon:"extra_value,omitempty"`
	SDKVersion  string `json:"sdk_version,omitempty" toon:"sdk_version,omitempty"`
	Throwable   bool   `json:"throwable,omitempty" toon:"throwable,omitempty"`
	Limit       int    `json:"limit" toon:"limit"`
	Offset      int    `json:"offset,omitempty" toon:"offset,omitempty"`
	Order       string `json:"order,omitempty" toon:"order,omitempty"`
}

// telemetryQueryResponse is the full response envelope returned to callers.
type telemetryQueryResponse struct {
	OK           bool                  `json:"ok" toon:"ok"`
	Project      string                `json:"project,omitempty" toon:"project,omitempty"`
	ConfigSource string                `json:"config_source,omitempty" toon:"config_source,omitempty"`
	Source       string                `json:"source" toon:"source"`
	Count        int                   `json:"count" toon:"count"`
	Request      telemetryQueryRequest `json:"request" toon:"request"`
	Rows         []map[string]any      `json:"rows" toon:"rows"`
	Summary      *telemetryAggregation `json:"summary,omitempty" toon:"summary,omitempty"`
}

// severityRank maps log level strings to a numeric rank for min-severity filtering.
// "metric" is intentionally excluded: METRIC events never land in app_logs
// (they go to app_metrics), so including it in an IN() filter is semantically wrong.
var severityRank = map[string]int{
	"debug":    0,
	"info":     1,
	"warn":     2,
	"error":    3,
	"critical": 4,
}

// severityAtOrAbove returns all severity values >= the given minimum level.
func severityAtOrAbove(minLevel string) []string {
	minRank, ok := severityRank[strings.ToLower(minLevel)]
	if !ok {
		return []string{strings.ToUpper(minLevel)}
	}
	result := make([]string, 0, 4)
	for level, rank := range severityRank {
		if rank >= minRank {
			result = append(result, strings.ToUpper(level))
		}
	}
	return result
}

// logsSelectColumns are all columns returned for app_logs queries.
const logsSelectColumns = "id,created_at,level,tag,message,session_id,device_id,user_id,environment,sdk_version,extra,anomaly_type"

// logsSelectColumnsWithThrowable includes throwable_info for debugging.
const logsSelectColumnsWithThrowable = "id,created_at,level,tag,message,session_id,device_id,user_id,environment,sdk_version,extra,anomaly_type,throwable_type,throwable_msg,stack_trace"

// metricsSelectColumns are all columns returned for app_metrics queries.
// NOTE: the SDK serializes metric tags as "tags" (SupabaseMetricEntry.tags field).
const metricsSelectColumns = "id,created_at,name,value,unit,tags,device_id,session_id,environment,sdk_version"

// supabaseHTTPClient returns an *http.Client with the configured timeout.
// Called once per command invocation — the returned client is reused across
// all retry attempts within that invocation, enabling TCP connection pooling.
func supabaseHTTPClient(cfg supabaseConfig) *http.Client {
	return &http.Client{Timeout: cfg.timeout()}
}

func queryTelemetry(ctx context.Context, cfg supabaseConfig, req telemetryQueryRequest) (telemetryQueryResponse, error) {
	rows, err := doQueryWithRetry(ctx, cfg, req, 3)
	if err != nil {
		return telemetryQueryResponse{}, err
	}

	summary, err := buildAggregation(req.Aggregate, rows)
	if err != nil {
		return telemetryQueryResponse{}, err
	}

	return telemetryQueryResponse{
		OK:      true,
		Source:  req.Source,
		Count:   len(rows),
		Request: req,
		Rows:    rows,
		Summary: summary,
	}, nil
}

// doQueryWithRetry executes the HTTP query against Supabase with automatic retry on 429/503.
// The http.Client is created once and reused across all retry attempts to enable TCP connection pooling.
func doQueryWithRetry(ctx context.Context, cfg supabaseConfig, req telemetryQueryRequest, maxAttempts int) ([]map[string]any, error) {
	var lastErr error
	backoff := []time.Duration{0, 2 * time.Second, 6 * time.Second}

	// Create the client once — reused across retries for TCP connection pooling.
	client := supabaseHTTPClient(cfg)

	for attempt := 0; attempt < maxAttempts; attempt++ {
		if attempt > 0 {
			wait := backoff[attempt]
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(wait):
			}
		}

		rows, retryAfter, retryable, err := doQuery(ctx, cfg, req, client)
		if err == nil {
			return rows, nil
		}
		lastErr = err
		if !retryable {
			return nil, err
		}
		// Respect Retry-After from HTTP 429 if present and larger than the default backoff.
		if retryAfter > 0 && attempt+1 < maxAttempts {
			backoff[attempt+1] = retryAfter
		}
	}
	return nil, lastErr
}

// doQuery executes a single HTTP request to Supabase REST API.
// Returns (rows, retryAfterDuration, retryable, error).
// retryAfterDuration is non-zero only on HTTP 429 with a Retry-After header.
func doQuery(ctx context.Context, cfg supabaseConfig, req telemetryQueryRequest, client *http.Client) ([]map[string]any, time.Duration, bool, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return nil, 0, false, fmt.Errorf("invalid Supabase URL: %w", err)
	}

	table := cfg.LogsTable
	selectCols := logsSelectColumns
	if req.Throwable {
		selectCols = logsSelectColumnsWithThrowable
	}
	if req.Source == "metrics" {
		table = cfg.MetricsTable
		selectCols = metricsSelectColumns
	}

	base.Path = path.Join(base.Path, "rest", "v1", table)
	query := base.Query()
	query.Set("select", selectCols)

	// Ordering
	orderCol := "created_at"
	orderDir := "desc"
	if req.Order == "asc" {
		orderDir = "asc"
	}
	query.Set("order", orderCol+"."+orderDir)

	// Pagination
	query.Set("limit", fmt.Sprintf("%d", req.Limit))
	if req.Offset > 0 {
		query.Set("offset", fmt.Sprintf("%d", req.Offset))
	}

	// Time range filters
	query.Del("created_at")
	if req.From != "" {
		query.Add("created_at", "gte."+req.From)
	}
	if req.To != "" {
		query.Add("created_at", "lte."+req.To)
	}

	// Common filters (both sources)
	if req.SessionID != "" {
		query.Set("session_id", "eq."+req.SessionID)
	}
	if req.DeviceID != "" {
		query.Set("device_id", "eq."+req.DeviceID)
	}
	if req.Environment != "" {
		query.Set("environment", "eq."+req.Environment)
	}
	if req.SDKVersion != "" {
		query.Set("sdk_version", "eq."+req.SDKVersion)
	}

	// Logs-only filters
	if req.Source == "logs" {
		if req.UserID != "" {
			query.Set("user_id", "eq."+req.UserID)
		}
		if req.Package != "" {
			query.Set("extra->>package_name", "eq."+req.Package)
		}
		if req.ErrorCode != "" {
			query.Set("extra->>error_code", "eq."+req.ErrorCode)
		}
		if req.Contains != "" {
			query.Set("message", "ilike.*"+req.Contains+"*")
		}
		if req.Fingerprint != "" {
			query.Set("extra->>device_fingerprint", "eq."+req.Fingerprint)
		}
		if req.Tag != "" {
			query.Set("tag", "eq."+req.Tag)
		}
		if req.AnomalyType != "" {
			query.Set("anomaly_type", "eq."+req.AnomalyType)
		}
		if req.TraceID != "" {
			query.Set("trace_id", "eq."+req.TraceID)
		}
		if req.Variant != "" {
			query.Set("variant", "eq."+req.Variant)
		}
		if req.BatchID != "" {
			query.Set("batch_id", "eq."+req.BatchID)
		}
		// Generic JSONB extra filter
		if req.ExtraKey != "" && req.ExtraValue != "" {
			query.Set("extra->>"+req.ExtraKey, "eq."+req.ExtraValue)
		}
		// Severity: exact match
		if req.Severity != "" {
			query.Set("level", "eq."+strings.ToUpper(req.Severity))
		}
		// Min-severity: range filter using Supabase `in` operator
		if req.MinSeverity != "" && req.Severity == "" {
			levels := severityAtOrAbove(req.MinSeverity)
			if len(levels) > 0 {
				query.Set("level", "in.("+strings.Join(levels, ",")+")")
			}
		}
	}

	// Metrics-only filters
	if req.Source == "metrics" {
		if req.Name != "" {
			query.Set("name", "eq."+req.Name)
		}
	}

	base.RawQuery = query.Encode()

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return nil, 0, false, fmt.Errorf("failed to create request: %w", err)
	}
	httpReq.Header.Set("apikey", cfg.APIKey)
	httpReq.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	httpReq.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		httpReq.Header.Set("Accept-Profile", cfg.Schema)
	}

	httpResp, err := client.Do(httpReq)
	if err != nil {
		return nil, 0, true, fmt.Errorf("supabase request failed: %w", err)
	}
	defer func() { _ = httpResp.Body.Close() }()

	body, err := io.ReadAll(httpResp.Body)
	if err != nil {
		return nil, 0, false, fmt.Errorf("failed to read Supabase response: %w", err)
	}

	if httpResp.StatusCode < 200 || httpResp.StatusCode >= 300 {
		retryAfter, retryable, httpErr := classifyHTTPError(httpResp.StatusCode, body, httpResp.Header)
		return nil, retryAfter, retryable, httpErr
	}

	rows := make([]map[string]any, 0)
	if len(strings.TrimSpace(string(body))) > 0 {
		if err := json.Unmarshal(body, &rows); err != nil {
			return nil, 0, false, fmt.Errorf("invalid Supabase JSON response: %w", err)
		}
	}
	return rows, 0, false, nil
}

// classifyHTTPError returns (retryAfterDuration, retryable, error).
// retryAfterDuration is non-zero only on HTTP 429 with a parseable Retry-After header (seconds).
func classifyHTTPError(status int, body []byte, header http.Header) (time.Duration, bool, error) {
	snippet := strings.TrimSpace(string(body))
	if len(snippet) > 200 {
		snippet = snippet[:200] + "..."
	}
	switch status {
	case 401:
		return 0, false, fmt.Errorf("authentication failed (HTTP 401): the API key is invalid or expired — check supabase.api_key in cli.json. detail: %s", snippet)
	case 403:
		return 0, false, fmt.Errorf("permission denied (HTTP 403): the API key lacks SELECT permission on the table — verify RLS policies in Supabase. detail: %s", snippet)
	case 404:
		return 0, false, fmt.Errorf("table not found (HTTP 404): the configured table does not exist — run the AppLoggers migrations or check logs_table/metrics_table in cli.json. detail: %s", snippet)
	case 429:
		var retryAfter time.Duration
		if ra := strings.TrimSpace(header.Get("Retry-After")); ra != "" {
			if secs, err := strconv.ParseInt(ra, 10, 64); err == nil && secs > 0 {
				retryAfter = time.Duration(secs) * time.Second
			}
		}
		return retryAfter, true, fmt.Errorf("rate limited by Supabase (HTTP 429): too many requests — will retry automatically. detail: %s", snippet)
	case 503:
		return 0, true, fmt.Errorf("Supabase unavailable (HTTP 503): service temporarily down — will retry automatically. detail: %s", snippet)
	default:
		retryable := status >= 500
		return 0, retryable, fmt.Errorf("supabase query failed: status=%d body=%s", status, snippet)
	}
}
