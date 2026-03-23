package cli

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"
)

type telemetryQueryRequest struct {
	Source      string `json:"source" toon:"source"`
	Aggregate   string `json:"aggregate,omitempty" toon:"aggregate,omitempty"`
	From        string `json:"from,omitempty" toon:"from,omitempty"`
	To          string `json:"to,omitempty" toon:"to,omitempty"`
	Severity    string `json:"severity,omitempty" toon:"severity,omitempty"`
	SessionID   string `json:"session_id,omitempty" toon:"session_id,omitempty"`
	DeviceID    string `json:"device_id,omitempty" toon:"device_id,omitempty"`
	UserID      string `json:"user_id,omitempty" toon:"user_id,omitempty"`
	Package     string `json:"package,omitempty" toon:"package,omitempty"`
	ErrorCode   string `json:"error_code,omitempty" toon:"error_code,omitempty"`
	Contains    string `json:"contains,omitempty" toon:"contains,omitempty"`
	Tag         string `json:"tag,omitempty" toon:"tag,omitempty"`
	Name        string `json:"name,omitempty" toon:"name,omitempty"`
	AnomalyType string `json:"anomaly_type,omitempty" toon:"anomaly_type,omitempty"`
	Limit       int    `json:"limit" toon:"limit"`
}

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

func queryTelemetry(ctx context.Context, cfg supabaseConfig, req telemetryQueryRequest) (telemetryQueryResponse, error) {
	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return telemetryQueryResponse{}, fmt.Errorf("invalid Supabase URL: %w", err)
	}

	table := cfg.LogsTable
	selectColumns := "id,created_at,level,tag,message,session_id,device_id,user_id,sdk_version,extra"
	if req.Source == "metrics" {
		table = cfg.MetricsTable
		selectColumns = "id,created_at,name,value,unit,tags,device_id,session_id,sdk_version"
	}

	base.Path = path.Join(base.Path, "rest", "v1", table)
	query := base.Query()
	query.Set("select", selectColumns)
	query.Set("order", "created_at.desc")
	query.Set("limit", fmt.Sprintf("%d", req.Limit))
	query.Del("created_at")
	if req.From != "" {
		query.Add("created_at", "gte."+req.From)
	}
	if req.To != "" {
		query.Add("created_at", "lte."+req.To)
	}
	if req.SessionID != "" {
		query.Set("session_id", "eq."+req.SessionID)
	}
	if req.DeviceID != "" {
		query.Set("device_id", "eq."+req.DeviceID)
	}
	if req.UserID != "" && req.Source == "logs" {
		query.Set("user_id", "eq."+req.UserID)
	}
	if req.Package != "" && req.Source == "logs" {
		query.Set("extra->>package_name", "eq."+req.Package)
	}
	if req.ErrorCode != "" && req.Source == "logs" {
		query.Set("extra->>error_code", "eq."+req.ErrorCode)
	}
	if req.Contains != "" && req.Source == "logs" {
		query.Set("message", "ilike.*"+req.Contains+"*")
	}
	if req.Tag != "" && req.Source == "logs" {
		query.Set("tag", "eq."+req.Tag)
	}
	if req.Name != "" && req.Source == "metrics" {
		query.Set("name", "eq."+req.Name)
	}
	if req.Severity != "" && req.Source == "logs" {
		query.Set("level", "eq."+strings.ToUpper(req.Severity))
	}
	if req.AnomalyType != "" && req.Source == "logs" {
		query.Set("extra->>anomaly_type", "eq."+req.AnomalyType)
	}
	base.RawQuery = query.Encode()

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return telemetryQueryResponse{}, fmt.Errorf("failed to create request: %w", err)
	}
	httpReq.Header.Set("apikey", cfg.APIKey)
	httpReq.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	httpReq.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		httpReq.Header.Set("Accept-Profile", cfg.Schema)
	}

	client := &http.Client{Timeout: cfg.timeout()}
	httpResp, err := client.Do(httpReq)
	if err != nil {
		return telemetryQueryResponse{}, fmt.Errorf("supabase request failed: %w", err)
	}
	defer func() {
		_ = httpResp.Body.Close()
	}()

	body, err := io.ReadAll(httpResp.Body)
	if err != nil {
		return telemetryQueryResponse{}, fmt.Errorf("failed to read Supabase response: %w", err)
	}
	if httpResp.StatusCode < 200 || httpResp.StatusCode >= 300 {
		return telemetryQueryResponse{}, fmt.Errorf("supabase query failed: status=%d body=%s", httpResp.StatusCode, string(body))
	}

	rows := make([]map[string]any, 0)
	if len(strings.TrimSpace(string(body))) > 0 {
		if err := json.Unmarshal(body, &rows); err != nil {
			return telemetryQueryResponse{}, fmt.Errorf("invalid Supabase JSON response: %w", err)
		}
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
