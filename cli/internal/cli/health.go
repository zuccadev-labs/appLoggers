package cli

import (
	"context"
	"fmt"
	"time"

	"github.com/spf13/cobra"
)

type healthPayload struct {
	OK           bool              `json:"ok"`
	Status       string            `json:"status"`
	Version      string            `json:"version"`
	Project      string            `json:"project,omitempty"`
	ConfigSource string            `json:"config_source,omitempty"`
	Timestamp    string            `json:"timestamp"`
	Deep         *healthDeepResult `json:"deep,omitempty"`
}

type healthDeepResult struct {
	SupabaseReachable bool   `json:"supabase_reachable"`
	LatencyMs         int64  `json:"latency_ms,omitempty"`
	LogsTableOK       bool   `json:"logs_table_ok"`
	MetricsTableOK    bool   `json:"metrics_table_ok"`
	Error             string `json:"error,omitempty"`
}

func newHealthCommand() *cobra.Command {
	var deepFlag bool

	cmd := &cobra.Command{
		Use:   "health",
		Short: "Health probe for automation and agents",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("health does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			payload := healthPayload{
				OK:        true,
				Status:    "ready",
				Version:   buildVersion,
				Timestamp: time.Now().UTC().Format(time.RFC3339),
			}

			cfg, cfgErr := loadSupabaseConfig()
			if cfgErr == nil {
				payload.Project = cfg.Project
				payload.ConfigSource = cfg.ConfigSource
			}

			if deepFlag {
				if cfgErr != nil {
					payload.OK = false
					payload.Status = "degraded"
					payload.Deep = &healthDeepResult{
						SupabaseReachable: false,
						Error:             fmt.Sprintf("config error: %s", cfgErr),
					}
				} else {
					deep := probeSupabase(cfg)
					payload.Deep = deep
					if !deep.SupabaseReachable || !deep.LogsTableOK {
						payload.OK = false
						payload.Status = "degraded"
					}
				}
			}

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), payload)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), payload)
			}

			_, err := fmt.Fprintf(cmd.OutOrStdout(), "status: %s\nversion: %s\n", payload.Status, payload.Version)
			if err != nil {
				return err
			}
			if payload.Deep != nil {
				_, err = fmt.Fprintf(cmd.OutOrStdout(),
					"supabase_reachable: %v\nlatency_ms: %d\nlogs_table_ok: %v\nmetrics_table_ok: %v\n",
					payload.Deep.SupabaseReachable,
					payload.Deep.LatencyMs,
					payload.Deep.LogsTableOK,
					payload.Deep.MetricsTableOK,
				)
			}
			return err
		},
	}

	cmd.Flags().BoolVar(&deepFlag, "deep", false, "Perform a real connectivity probe against Supabase and report latency")
	return cmd
}

// probeSupabase performs a lightweight query against both tables to verify connectivity.
func probeSupabase(cfg supabaseConfig) *healthDeepResult {
	result := &healthDeepResult{}

	// Probe logs table with limit=1
	start := time.Now()
	logsReq := telemetryQueryRequest{Source: "logs", Limit: 1, Order: "desc"}
	ctx, cancel := context.WithTimeout(context.Background(), cfg.timeout())
	_, err := queryTelemetry(ctx, cfg, logsReq)
	cancel() // explicit cancel — don't defer to avoid holding the context until function return
	result.LatencyMs = time.Since(start).Milliseconds()

	if err != nil {
		result.SupabaseReachable = false
		result.Error = err.Error()
		return result
	}
	result.SupabaseReachable = true
	result.LogsTableOK = true

	// Probe metrics table
	metricsReq := telemetryQueryRequest{Source: "metrics", Limit: 1, Order: "desc"}
	ctx2, cancel2 := context.WithTimeout(context.Background(), cfg.timeout())
	_, metricsErr := queryTelemetry(ctx2, cfg, metricsReq)
	cancel2() // explicit cancel
	result.MetricsTableOK = metricsErr == nil

	return result
}
