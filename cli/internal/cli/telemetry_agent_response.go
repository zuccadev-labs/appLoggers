package cli

type telemetryAgentResponse struct {
	Kind         string                `json:"kind" toon:"kind"`
	OK           bool                  `json:"ok" toon:"ok"`
	Project      string                `json:"project,omitempty" toon:"project,omitempty"`
	ConfigSource string                `json:"config_source,omitempty" toon:"config_source,omitempty"`
	Source       string                `json:"source" toon:"source"`
	Count        int                   `json:"count" toon:"count"`
	Request      telemetryQueryRequest `json:"request" toon:"request"`
	Summary      *telemetryAggregation `json:"summary,omitempty" toon:"summary,omitempty"`
	RowsPreview  []map[string]any      `json:"rows_preview,omitempty" toon:"rows_preview,omitempty"`
	Hints        []string              `json:"hints" toon:"hints"`
}

func buildTelemetryAgentResponse(resp telemetryQueryResponse, previewLimit int) telemetryAgentResponse {
	rowsPreview := make([]map[string]any, 0)
	if previewLimit > 0 {
		limit := previewLimit
		if len(resp.Rows) < limit {
			limit = len(resp.Rows)
		}
		rowsPreview = append(rowsPreview, resp.Rows[:limit]...)
	}

	hints := []string{
		"Use telemetry query --output json for complete raw payloads.",
		"Use --aggregate hour|day|week|severity|tag|session|name|environment for compact summaries.",
		"Use --offset N for pagination beyond the first page.",
		"Use --environment production|staging|development to isolate environments.",
	}
	if resp.Source == "metrics" {
		hints = append(hints, "For metrics insights, prefer --aggregate name.")
	} else {
		hints = append(hints, "For log triage, prefer --aggregate severity and --min-severity error.")
		hints = append(hints, "Use --throwable to include stack traces in the response.")
	}

	return telemetryAgentResponse{
		Kind:         "telemetry_agent_response",
		OK:           resp.OK,
		Project:      resp.Project,
		ConfigSource: resp.ConfigSource,
		Source:       resp.Source,
		Count:        resp.Count,
		Request:      resp.Request,
		Summary:      resp.Summary,
		RowsPreview:  rowsPreview,
		Hints:        hints,
	}
}
