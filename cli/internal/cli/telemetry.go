package cli

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

func newTelemetryCommand() *cobra.Command {
	telemetryCmd := &cobra.Command{
		Use:   "telemetry",
		Short: "Telemetry operations",
	}

	var (
		sourceFlag    string
		aggregateFlag string
		fromFlag      string
		toFlag        string
		severityFlag  string
		sessionFlag   string
		tagFlag       string
		nameFlag      string
		limitFlag     int
	)

	buildRequest := func() (telemetryQueryRequest, error) {
		if limitFlag < 1 || limitFlag > 1000 {
			return telemetryQueryRequest{}, newUsageError("invalid --limit value %d (expected 1..1000)", limitFlag)
		}

		aggregate := strings.ToLower(strings.TrimSpace(aggregateFlag))
		switch aggregate {
		case "", "none", "hour", "severity", "tag", "session", "name":
		default:
			return telemetryQueryRequest{}, newUsageError("invalid --aggregate value %q (expected none|hour|severity|tag|session|name)", aggregateFlag)
		}

		source := strings.ToLower(strings.TrimSpace(sourceFlag))
		switch source {
		case "logs", "metrics":
		default:
			return telemetryQueryRequest{}, newUsageError("invalid --source value %q (expected logs|metrics)", sourceFlag)
		}

		if fromFlag != "" {
			if _, err := time.Parse(time.RFC3339, fromFlag); err != nil {
				return telemetryQueryRequest{}, newUsageError("invalid --from value %q (expected RFC3339)", fromFlag)
			}
		}
		if toFlag != "" {
			if _, err := time.Parse(time.RFC3339, toFlag); err != nil {
				return telemetryQueryRequest{}, newUsageError("invalid --to value %q (expected RFC3339)", toFlag)
			}
		}

		severity := strings.ToLower(strings.TrimSpace(severityFlag))
		if severity != "" {
			if source != "logs" {
				return telemetryQueryRequest{}, newUsageError("--severity is only valid when --source=logs")
			}
			switch severity {
			case "debug", "info", "warn", "error", "critical", "metric":
			default:
				return telemetryQueryRequest{}, newUsageError("invalid --severity value %q", severityFlag)
			}
		}

		if aggregate == "severity" || aggregate == "tag" {
			if source != "logs" {
				return telemetryQueryRequest{}, newUsageError("--aggregate=%s is only valid when --source=logs", aggregate)
			}
		}
		if aggregate == "name" {
			if source != "metrics" {
				return telemetryQueryRequest{}, newUsageError("--aggregate=name is only valid when --source=metrics")
			}
		}

		if strings.TrimSpace(tagFlag) != "" && source != "logs" {
			return telemetryQueryRequest{}, newUsageError("--tag is only valid when --source=logs")
		}
		if strings.TrimSpace(nameFlag) != "" && source != "metrics" {
			return telemetryQueryRequest{}, newUsageError("--name is only valid when --source=metrics")
		}

		return telemetryQueryRequest{
			Source:    source,
			Aggregate: aggregate,
			From:      fromFlag,
			To:        toFlag,
			Severity:  severity,
			SessionID: strings.TrimSpace(sessionFlag),
			Tag:       strings.TrimSpace(tagFlag),
			Name:      strings.TrimSpace(nameFlag),
			Limit:     limitFlag,
		}, nil
	}

	execute := func(req telemetryQueryRequest) (telemetryQueryResponse, error) {
		cfg, err := loadSupabaseConfig()
		if err != nil {
			return telemetryQueryResponse{}, err
		}
		return queryTelemetry(context.Background(), cfg, req)
	}

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

			req, err := buildRequest()
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
				"source=%s\ncount=%d\naggregate=%s\nfrom=%s\nto=%s\nseverity=%s\nsession_id=%s\ntag=%s\nname=%s\nlimit=%d\n",
				response.Source,
				response.Count,
				response.Request.Aggregate,
				response.Request.From,
				response.Request.To,
				response.Request.Severity,
				response.Request.SessionID,
				response.Request.Tag,
				response.Request.Name,
				response.Request.Limit,
			)
			return err
		},
	}

	var previewLimitFlag int
	agentResponseCmd := &cobra.Command{
		Use:   "agent-response",
		Short: "Query telemetry and emit compact agent response",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry agent-response does not accept positional arguments")
			}
			if previewLimitFlag < 0 || previewLimitFlag > 50 {
				return newUsageError("invalid --preview-limit value %d (expected 0..50)", previewLimitFlag)
			}
			req, err := buildRequest()
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

	queryCmd.Flags().StringVar(&sourceFlag, "source", "logs", "Telemetry source: logs|metrics")
	queryCmd.Flags().StringVar(&aggregateFlag, "aggregate", "none", "Aggregation mode: none|hour|severity|tag|session|name")
	queryCmd.Flags().StringVar(&fromFlag, "from", "", "Start timestamp (RFC3339)")
	queryCmd.Flags().StringVar(&toFlag, "to", "", "End timestamp (RFC3339)")
	queryCmd.Flags().StringVar(&severityFlag, "severity", "", "Severity filter for logs: debug|info|warn|error|critical|metric")
	queryCmd.Flags().StringVar(&sessionFlag, "session-id", "", "Session UUID filter")
	queryCmd.Flags().StringVar(&tagFlag, "tag", "", "Tag filter (logs source only)")
	queryCmd.Flags().StringVar(&nameFlag, "name", "", "Metric name filter (metrics source only)")
	queryCmd.Flags().IntVar(&limitFlag, "limit", 100, "Result size limit (1..1000)")

	agentResponseCmd.Flags().StringVar(&sourceFlag, "source", "logs", "Telemetry source: logs|metrics")
	agentResponseCmd.Flags().StringVar(&aggregateFlag, "aggregate", "none", "Aggregation mode: none|hour|severity|tag|session|name")
	agentResponseCmd.Flags().StringVar(&fromFlag, "from", "", "Start timestamp (RFC3339)")
	agentResponseCmd.Flags().StringVar(&toFlag, "to", "", "End timestamp (RFC3339)")
	agentResponseCmd.Flags().StringVar(&severityFlag, "severity", "", "Severity filter for logs: debug|info|warn|error|critical|metric")
	agentResponseCmd.Flags().StringVar(&sessionFlag, "session-id", "", "Session UUID filter")
	agentResponseCmd.Flags().StringVar(&tagFlag, "tag", "", "Tag filter (logs source only)")
	agentResponseCmd.Flags().StringVar(&nameFlag, "name", "", "Metric name filter (metrics source only)")
	agentResponseCmd.Flags().IntVar(&limitFlag, "limit", 100, "Result size limit (1..1000)")
	agentResponseCmd.Flags().IntVar(&previewLimitFlag, "preview-limit", 5, "Max rows included in rows_preview for agents (0..50)")

	telemetryCmd.AddCommand(queryCmd)
	telemetryCmd.AddCommand(agentResponseCmd)

	return telemetryCmd
}
