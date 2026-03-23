package cli

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

// telemetryFlags holds all filter/pagination state shared across telemetry subcommands.
type telemetryFlags struct {
	source      string
	aggregate   string
	from        string
	to          string
	severity    string
	minSeverity string
	environment string
	session     string
	deviceID    string
	userID      string
	pkg         string
	errorCode   string
	contains    string
	tag         string
	name        string
	anomalyType string
	extraKey    string
	extraValue  string
	sdkVersion  string
	throwable   bool
	limit       int
	offset      int
	order       string
}

// addTelemetryFlags registers all shared telemetry filter flags on a command.
// This eliminates the duplication that previously existed between query and agent-response.
func addTelemetryFlags(cmd *cobra.Command, f *telemetryFlags) {
	cmd.Flags().StringVar(&f.source, "source", "logs", "Telemetry source: logs|metrics")
	cmd.Flags().StringVar(&f.aggregate, "aggregate", "none", "Aggregation mode: none|hour|day|week|severity|tag|session|name|environment")
	cmd.Flags().StringVar(&f.from, "from", "", "Start timestamp (RFC3339)")
	cmd.Flags().StringVar(&f.to, "to", "", "End timestamp (RFC3339)")
	cmd.Flags().StringVar(&f.severity, "severity", "", "Exact severity filter (logs): debug|info|warn|error|critical|metric")
	cmd.Flags().StringVar(&f.minSeverity, "min-severity", "", "Minimum severity filter (logs): debug|info|warn|error|critical — includes all levels at or above")
	cmd.Flags().StringVar(&f.environment, "environment", "", "Environment filter: production|staging|development (logs and metrics)")
	cmd.Flags().StringVar(&f.session, "session-id", "", "Session identifier filter")
	cmd.Flags().StringVar(&f.deviceID, "device-id", "", "Device identifier filter")
	cmd.Flags().StringVar(&f.userID, "user-id", "", "Anonymous user identifier filter (logs only)")
	cmd.Flags().StringVar(&f.pkg, "package", "", "Package/module filter from extra.package_name (logs only)")
	cmd.Flags().StringVar(&f.errorCode, "error-code", "", "Error code filter from extra.error_code (logs only)")
	cmd.Flags().StringVar(&f.contains, "contains", "", "Message substring filter (logs only)")
	cmd.Flags().StringVar(&f.tag, "tag", "", "Tag filter (logs only)")
	cmd.Flags().StringVar(&f.name, "name", "", "Metric name filter (metrics only)")
	cmd.Flags().StringVar(&f.anomalyType, "anomaly-type", "", "Anomaly type filter (logs only, e.g. slow_response)")
	cmd.Flags().StringVar(&f.extraKey, "extra-key", "", "JSONB extra field key for ad-hoc filter (logs only, use with --extra-value)")
	cmd.Flags().StringVar(&f.extraValue, "extra-value", "", "JSONB extra field value for ad-hoc filter (logs only, use with --extra-key)")
	cmd.Flags().StringVar(&f.sdkVersion, "sdk-version", "", "SDK version filter (e.g. 0.2.0)")
	cmd.Flags().BoolVar(&f.throwable, "throwable", false, "Include throwable_type, throwable_msg and stack_trace columns in response (logs only)")
	cmd.Flags().IntVar(&f.limit, "limit", 100, "Result size limit (1..1000)")
	cmd.Flags().IntVar(&f.offset, "offset", 0, "Result offset for pagination (0-based)")
	cmd.Flags().StringVar(&f.order, "order", "desc", "Sort order by created_at: desc|asc")
}

// buildRequest validates the flags and constructs a telemetryQueryRequest.
func (f *telemetryFlags) buildRequest() (telemetryQueryRequest, error) {
	if f.limit < 1 || f.limit > 1000 {
		return telemetryQueryRequest{}, newUsageError("invalid --limit value %d (expected 1..1000)", f.limit)
	}
	if f.offset < 0 {
		return telemetryQueryRequest{}, newUsageError("invalid --offset value %d (expected >= 0)", f.offset)
	}

	order := strings.ToLower(strings.TrimSpace(f.order))
	switch order {
	case "desc", "asc", "":
	default:
		return telemetryQueryRequest{}, newUsageError("invalid --order value %q (expected desc|asc)", f.order)
	}
	if order == "" {
		order = "desc"
	}

	aggregate := strings.ToLower(strings.TrimSpace(f.aggregate))
	validAgg := false
	for _, m := range validAggregateModes {
		if aggregate == m {
			validAgg = true
			break
		}
	}
	if !validAgg {
		return telemetryQueryRequest{}, newUsageError(
			"invalid --aggregate value %q (expected %s)",
			f.aggregate, strings.Join(validAggregateModes, "|"),
		)
	}

	source := strings.ToLower(strings.TrimSpace(f.source))
	switch source {
	case "logs", "metrics":
	default:
		return telemetryQueryRequest{}, newUsageError("invalid --source value %q (expected logs|metrics)", f.source)
	}

	if f.from != "" {
		if _, err := time.Parse(time.RFC3339, f.from); err != nil {
			return telemetryQueryRequest{}, newUsageError("invalid --from value %q (expected RFC3339)", f.from)
		}
	}
	if f.to != "" {
		if _, err := time.Parse(time.RFC3339, f.to); err != nil {
			return telemetryQueryRequest{}, newUsageError("invalid --to value %q (expected RFC3339)", f.to)
		}
	}

	severity := strings.ToLower(strings.TrimSpace(f.severity))
	if severity != "" {
		if source != "logs" {
			return telemetryQueryRequest{}, newUsageError("--severity is only valid when --source=logs")
		}
		switch severity {
		case "debug", "info", "warn", "error", "critical", "metric":
		default:
			return telemetryQueryRequest{}, newUsageError("invalid --severity value %q (expected debug|info|warn|error|critical|metric)", f.severity)
		}
	}

	minSeverity := strings.ToLower(strings.TrimSpace(f.minSeverity))
	if minSeverity != "" {
		if source != "logs" {
			return telemetryQueryRequest{}, newUsageError("--min-severity is only valid when --source=logs")
		}
		switch minSeverity {
		case "debug", "info", "warn", "error", "critical":
		default:
			return telemetryQueryRequest{}, newUsageError("invalid --min-severity value %q (expected debug|info|warn|error|critical)", f.minSeverity)
		}
		if severity != "" {
			return telemetryQueryRequest{}, newUsageError("--severity and --min-severity are mutually exclusive")
		}
	}

	// Aggregate source constraints
	switch aggregate {
	case "severity", "tag":
		if source != "logs" {
			return telemetryQueryRequest{}, newUsageError("--aggregate=%s is only valid when --source=logs", aggregate)
		}
	case "name":
		if source != "metrics" {
			return telemetryQueryRequest{}, newUsageError("--aggregate=name is only valid when --source=metrics")
		}
	}

	// Logs-only flag validation
	if source != "logs" {
		if strings.TrimSpace(f.tag) != "" {
			return telemetryQueryRequest{}, newUsageError("--tag is only valid when --source=logs")
		}
		if strings.TrimSpace(f.anomalyType) != "" {
			return telemetryQueryRequest{}, newUsageError("--anomaly-type is only valid when --source=logs")
		}
		if strings.TrimSpace(f.userID) != "" {
			return telemetryQueryRequest{}, newUsageError("--user-id is only valid when --source=logs")
		}
		if strings.TrimSpace(f.pkg) != "" {
			return telemetryQueryRequest{}, newUsageError("--package is only valid when --source=logs")
		}
		if strings.TrimSpace(f.errorCode) != "" {
			return telemetryQueryRequest{}, newUsageError("--error-code is only valid when --source=logs")
		}
		if strings.TrimSpace(f.contains) != "" {
			return telemetryQueryRequest{}, newUsageError("--contains is only valid when --source=logs")
		}
		if strings.TrimSpace(f.extraKey) != "" {
			return telemetryQueryRequest{}, newUsageError("--extra-key is only valid when --source=logs")
		}
		if f.throwable {
			return telemetryQueryRequest{}, newUsageError("--throwable is only valid when --source=logs")
		}
	}

	// Metrics-only flag validation
	if source != "metrics" && strings.TrimSpace(f.name) != "" {
		return telemetryQueryRequest{}, newUsageError("--name is only valid when --source=metrics")
	}

	// extra-key and extra-value must be used together
	if (strings.TrimSpace(f.extraKey) != "") != (strings.TrimSpace(f.extraValue) != "") {
		return telemetryQueryRequest{}, newUsageError("--extra-key and --extra-value must be used together")
	}

	return telemetryQueryRequest{
		Source:      source,
		Aggregate:   aggregate,
		From:        f.from,
		To:          f.to,
		Severity:    severity,
		MinSeverity: minSeverity,
		Environment: strings.TrimSpace(f.environment),
		SessionID:   strings.TrimSpace(f.session),
		DeviceID:    strings.TrimSpace(f.deviceID),
		UserID:      strings.TrimSpace(f.userID),
		Package:     strings.TrimSpace(f.pkg),
		ErrorCode:   strings.TrimSpace(f.errorCode),
		Contains:    strings.TrimSpace(f.contains),
		Tag:         strings.TrimSpace(f.tag),
		Name:        strings.TrimSpace(f.name),
		AnomalyType: strings.TrimSpace(f.anomalyType),
		ExtraKey:    strings.TrimSpace(f.extraKey),
		ExtraValue:  strings.TrimSpace(f.extraValue),
		SDKVersion:  strings.TrimSpace(f.sdkVersion),
		Throwable:   f.throwable,
		Limit:       f.limit,
		Offset:      f.offset,
		Order:       order,
	}, nil
}

func newTelemetryCommand() *cobra.Command {
	telemetryCmd := &cobra.Command{
		Use:   "telemetry",
		Short: "Telemetry operations: query, stream, tail, stats",
	}

	execute := func(req telemetryQueryRequest) (telemetryQueryResponse, error) {
		cfg, err := loadSupabaseConfig()
		if err != nil {
			return telemetryQueryResponse{}, err
		}
		response, err := queryTelemetry(context.Background(), cfg, req)
		if err != nil {
			return telemetryQueryResponse{}, err
		}
		response.Project = cfg.Project
		response.ConfigSource = cfg.ConfigSource
		return response, nil
	}

	// ── telemetry query ──────────────────────────────────────────────────────
	var queryFlags telemetryFlags
	queryCmd := &cobra.Command{
		Use:   "query",
		Short: "Query telemetry from Supabase",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry query does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			req, err := queryFlags.buildRequest()
			if err != nil {
				return err
			}
			response, err := execute(req)
			if err != nil {
				return err
			}
			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), response)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), response)
			}
			_, err = fmt.Fprintf(
				cmd.OutOrStdout(),
				"source=%s\ncount=%d\naggregate=%s\nfrom=%s\nto=%s\nseverity=%s\nmin_severity=%s\nenvironment=%s\nsession_id=%s\ndevice_id=%s\nuser_id=%s\npackage=%s\nerror_code=%s\ncontains=%s\ntag=%s\nname=%s\nlimit=%d\noffset=%d\norder=%s\n",
				response.Source,
				response.Count,
				response.Request.Aggregate,
				response.Request.From,
				response.Request.To,
				response.Request.Severity,
				response.Request.MinSeverity,
				response.Request.Environment,
				response.Request.SessionID,
				response.Request.DeviceID,
				response.Request.UserID,
				response.Request.Package,
				response.Request.ErrorCode,
				response.Request.Contains,
				response.Request.Tag,
				response.Request.Name,
				response.Request.Limit,
				response.Request.Offset,
				response.Request.Order,
			)
			return err
		},
	}
	addTelemetryFlags(queryCmd, &queryFlags)

	// ── telemetry agent-response ─────────────────────────────────────────────
	var agentFlags telemetryFlags
	var previewLimitFlag int
	agentResponseCmd := &cobra.Command{
		Use:   "agent-response",
		Short: "Query telemetry and emit compact agent response (TOON envelope)",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry agent-response does not accept positional arguments")
			}
			if previewLimitFlag < 0 || previewLimitFlag > 50 {
				return newUsageError("invalid --preview-limit value %d (expected 0..50)", previewLimitFlag)
			}
			req, err := agentFlags.buildRequest()
			if err != nil {
				return err
			}
			response, err := execute(req)
			if err != nil {
				return err
			}
			agentPayload := buildTelemetryAgentResponse(response, previewLimitFlag)
			return writeAgent(cmd.OutOrStdout(), agentPayload)
		},
	}
	addTelemetryFlags(agentResponseCmd, &agentFlags)
	agentResponseCmd.Flags().IntVar(&previewLimitFlag, "preview-limit", 5, "Max rows included in rows_preview for agents (0..50)")

	telemetryCmd.AddCommand(queryCmd)
	telemetryCmd.AddCommand(agentResponseCmd)
	telemetryCmd.AddCommand(newTelemetryStreamCommand())
	telemetryCmd.AddCommand(newTelemetryTailCommand())
	telemetryCmd.AddCommand(newTelemetryStatsCommand())

	return telemetryCmd
}
