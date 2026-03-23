package cli

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/spf13/cobra"
)

// ── telemetry stream ─────────────────────────────────────────────────────────
//
// Emits a continuous Server-Sent Events (SSE) stream to stdout.
// Designed to be consumed by a frontend EventSource or an HTTP proxy that
// forwards the SSE stream to browser clients.
//
// Output format (text/event-stream):
//
//	event: telemetry
//	data: {"ok":true,"source":"logs","count":3,"rows":[...]}
//
//	event: heartbeat
//	data: {"ts":"2026-03-23T10:00:00Z"}
func newTelemetryStreamCommand() *cobra.Command {
	var streamFlags telemetryFlags
	var intervalFlag int
	var maxEventsFlag int

	cmd := &cobra.Command{
		Use:   "stream",
		Short: "Stream telemetry as Server-Sent Events (SSE) — for frontend EventSource consumers",
		Long: `Polls Supabase at a configurable interval and emits each batch as an SSE event.

Output is written to stdout in text/event-stream format:

  event: telemetry
  data: <json payload>

  event: heartbeat
  data: {"ts":"..."}

Pipe this output through an HTTP server (e.g. a Go/Node proxy) to expose it
as a real SSE endpoint for browser clients. The stream runs until interrupted
with Ctrl+C or SIGTERM.

Note: --output is ignored for stream — output is always text/event-stream.`,
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry stream does not accept positional arguments")
			}
			if intervalFlag < 1 || intervalFlag > 300 {
				return newUsageError("invalid --interval value %d (expected 1..300 seconds)", intervalFlag)
			}
			if maxEventsFlag < 0 {
				return newUsageError("invalid --max-events value %d (expected >= 0, 0 = unlimited)", maxEventsFlag)
			}
			// stream always emits SSE regardless of --output; validate to catch typos
			if outputFormat != "text" && outputFormat != "json" && outputFormat != "agent" {
				return newUsageError("invalid --output value %q (expected text|json|agent)", outputFormat)
			}

			req, err := streamFlags.buildRequest()
			if err != nil {
				return err
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			out := cmd.OutOrStdout()
			ticker := time.NewTicker(time.Duration(intervalFlag) * time.Second)
			defer ticker.Stop()

			ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
			defer cancel()

			totalEmitted := 0
			var lastSeenAt string

			// Emit initial heartbeat so the client knows the stream is alive.
			writeSSEHeartbeat(out)

			for {
				select {
				case <-ctx.Done():
					return nil
				case <-ticker.C:
					pollReq := req
					if lastSeenAt != "" {
						pollReq.From = lastSeenAt
					}
					// Always order ascending when streaming so we get new events in order.
					pollReq.Order = "asc"

					response, err := queryTelemetry(ctx, cfg, pollReq)
					if err != nil {
						// Emit an error event — don't crash the stream.
						writeSSEError(out, err)
						continue
					}
					response.Project = cfg.Project
					response.ConfigSource = cfg.ConfigSource

					if response.Count > 0 {
						lastSeenAt = advanceCursor(response.Rows, lastSeenAt)
						writeSSEEvent(out, "telemetry", response)
						totalEmitted += response.Count
					} else {
						writeSSEHeartbeat(out)
					}

					if maxEventsFlag > 0 && totalEmitted >= maxEventsFlag {
						return nil
					}
				}
			}
		},
	}

	addTelemetryFlags(cmd, &streamFlags)
	cmd.Flags().IntVar(&intervalFlag, "interval", 5, "Polling interval in seconds (1..300)")
	cmd.Flags().IntVar(&maxEventsFlag, "max-events", 0, "Stop after emitting N total events (0 = unlimited)")

	return cmd
}

// ── telemetry tail ───────────────────────────────────────────────────────────
//
// Human-friendly follow mode — prints new log lines to stdout as they arrive.
// Equivalent to `tail -f` for AppLoggers telemetry.
func newTelemetryTailCommand() *cobra.Command {
	var tailFlags telemetryFlags
	var intervalFlag int

	cmd := &cobra.Command{
		Use:   "tail",
		Short: "Follow telemetry in real time — equivalent to tail -f for AppLoggers logs",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry tail does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			if intervalFlag < 1 || intervalFlag > 60 {
				return newUsageError("invalid --interval value %d (expected 1..60 seconds)", intervalFlag)
			}

			req, err := tailFlags.buildRequest()
			if err != nil {
				return err
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			out := cmd.OutOrStdout()
			ticker := time.NewTicker(time.Duration(intervalFlag) * time.Second)
			defer ticker.Stop()

			ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
			defer cancel()

			var lastSeenAt string
			_, _ = fmt.Fprintf(out, "Tailing %s (interval=%ds) — Ctrl+C to stop\n\n", req.Source, intervalFlag)

			for {
				select {
				case <-ctx.Done():
					return nil
				case <-ticker.C:
					pollReq := req
					if lastSeenAt != "" {
						pollReq.From = lastSeenAt
					}
					pollReq.Order = "asc"

					response, err := queryTelemetry(ctx, cfg, pollReq)
					if err != nil {
						if outputFormat == "json" {
							_ = writeJSON(out, map[string]any{"ok": false, "error": err.Error()})
						} else {
							_, _ = fmt.Fprintf(out, "[error] %s\n", err)
						}
						continue
					}

					if outputFormat == "json" {
						_ = writeJSON(out, response)
					} else {
						for _, row := range response.Rows {
							printTailRow(out, row, req.Source)
						}
					}

					if response.Count > 0 {
						lastSeenAt = advanceCursor(response.Rows, lastSeenAt)
					}
				}
			}
		},
	}

	addTelemetryFlags(cmd, &tailFlags)
	cmd.Flags().IntVar(&intervalFlag, "interval", 3, "Polling interval in seconds (1..60)")

	return cmd
}

// ── telemetry stats ──────────────────────────────────────────────────────────
//
// Quick statistical summary: error rate, top tags, events per hour, etc.
// Uses addTelemetryFlags so all standard filters are available.
type telemetryStats struct {
	OK           bool                  `json:"ok" toon:"ok"`
	Project      string                `json:"project,omitempty" toon:"project,omitempty"`
	ConfigSource string                `json:"config_source,omitempty" toon:"config_source,omitempty"`
	Source       string                `json:"source" toon:"source"`
	From         string                `json:"from,omitempty" toon:"from,omitempty"`
	To           string                `json:"to,omitempty" toon:"to,omitempty"`
	Environment  string                `json:"environment,omitempty" toon:"environment,omitempty"`
	TotalEvents  int                   `json:"total_events" toon:"total_events"`
	ErrorRate    float64               `json:"error_rate_pct" toon:"error_rate_pct"`
	BySeverity   *telemetryAggregation `json:"by_severity,omitempty" toon:"by_severity,omitempty"`
	ByTag        *telemetryAggregation `json:"by_tag,omitempty" toon:"by_tag,omitempty"`
	ByHour       *telemetryAggregation `json:"by_hour,omitempty" toon:"by_hour,omitempty"`
	ByEnv        *telemetryAggregation `json:"by_environment,omitempty" toon:"by_environment,omitempty"`
	ByName       *telemetryAggregation `json:"by_name,omitempty" toon:"by_name,omitempty"`
}

func newTelemetryStatsCommand() *cobra.Command {
	// stats uses the shared telemetryFlags so all standard filters are available
	// (--session-id, --device-id, --sdk-version, --severity, --min-severity, --tag, etc.)
	var statsFlags telemetryFlags

	cmd := &cobra.Command{
		Use:   "stats",
		Short: "Quick statistical summary: error rate, top tags, events per hour",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("telemetry stats does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			req, err := statsFlags.buildRequest()
			if err != nil {
				return err
			}

			// Warn when no --from is specified: stats over all historical data can be
			// slow and semantically misleading (mixes data from all time periods).
			if req.From == "" {
				_, _ = fmt.Fprint(cmd.ErrOrStderr(),
					"warning: --from not specified; stats will aggregate ALL historical data. "+
						"Consider adding --from (e.g. --from <RFC3339 timestamp>) "+
						"for faster and more meaningful results.\n")
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			ctx := context.Background()
			response, err := queryTelemetry(ctx, cfg, req)
			if err != nil {
				return err
			}

			stats := telemetryStats{
				OK:           true,
				Project:      cfg.Project,
				ConfigSource: cfg.ConfigSource,
				Source:       req.Source,
				From:         req.From,
				To:           req.To,
				Environment:  req.Environment,
				TotalEvents:  response.Count,
			}

			if req.Source == "logs" {
				bySeverity, _ := buildAggregation("severity", response.Rows)
				byTag, _ := buildAggregation("tag", response.Rows)
				byHour, _ := buildAggregation("hour", response.Rows)
				byEnv, _ := buildAggregation("environment", response.Rows)
				stats.BySeverity = bySeverity
				stats.ByTag = byTag
				stats.ByHour = byHour
				stats.ByEnv = byEnv
				stats.ErrorRate = computeErrorRate(bySeverity, response.Count)
			} else {
				byName, _ := buildAggregation("name", response.Rows)
				byHour, _ := buildAggregation("hour", response.Rows)
				byEnv, _ := buildAggregation("environment", response.Rows)
				stats.ByName = byName
				stats.ByHour = byHour
				stats.ByEnv = byEnv
			}

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), stats)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), stats)
			}

			out := cmd.OutOrStdout()
			_, _ = fmt.Fprintf(out, "source=%s total=%d error_rate=%.1f%%\n", stats.Source, stats.TotalEvents, stats.ErrorRate)
			if stats.BySeverity != nil {
				_, _ = fmt.Fprintln(out, "by_severity:")
				for _, b := range stats.BySeverity.Buckets {
					_, _ = fmt.Fprintf(out, "  %s: %d\n", b.Key, b.Count)
				}
			}
			if stats.ByTag != nil {
				_, _ = fmt.Fprintln(out, "top_tags:")
				for i, b := range stats.ByTag.Buckets {
					if i >= 5 {
						break
					}
					_, _ = fmt.Fprintf(out, "  %s: %d\n", b.Key, b.Count)
				}
			}
			return nil
		},
	}

	addTelemetryFlags(cmd, &statsFlags)
	// Override the default limit for stats — we want a larger sample by default.
	// We look up the flag after registration to avoid a panic if the flag name changes.
	if limitFlag := cmd.Flags().Lookup("limit"); limitFlag != nil {
		limitFlag.DefValue = "500"
		limitFlag.Value.Set("500") //nolint:errcheck // Set on IntVar always succeeds for valid int
	}

	return cmd
}

// ── SSE helpers ──────────────────────────────────────────────────────────────

func writeSSEEvent(out io.Writer, eventName string, payload any) {
	data, err := json.Marshal(payload)
	if err != nil {
		writeSSEError(out, err)
		return
	}
	_, _ = fmt.Fprintf(out, "event: %s\ndata: %s\n\n", eventName, string(data))
}

func writeSSEHeartbeat(out io.Writer) {
	_, _ = fmt.Fprintf(out, "event: heartbeat\ndata: {\"ts\":\"%s\"}\n\n", time.Now().UTC().Format(time.RFC3339))
}

func writeSSEError(out io.Writer, err error) {
	_, _ = fmt.Fprintf(out, "event: error\ndata: {\"error\":%q}\n\n", err.Error())
}

// ── tail helpers ─────────────────────────────────────────────────────────────

func printTailRow(out io.Writer, row map[string]any, source string) {
	ts := fmt.Sprint(row["created_at"])
	if len(ts) > 19 {
		ts = ts[:19] // trim to YYYY-MM-DDTHH:MM:SS
	}

	if source == "logs" {
		level := strings.ToUpper(fmt.Sprint(row["level"]))
		tag := fmt.Sprint(row["tag"])
		msg := fmt.Sprint(row["message"])
		env := fmt.Sprint(row["environment"])
		envStr := ""
		if env != "" && env != "<nil>" {
			envStr = "[" + env + "] "
		}
		_, _ = fmt.Fprintf(out, "%s %s%-8s %-12s %s\n", ts, envStr, level, tag, msg)
	} else {
		name := fmt.Sprint(row["name"])
		value := fmt.Sprint(row["value"])
		unit := fmt.Sprint(row["unit"])
		_, _ = fmt.Fprintf(out, "%s METRIC %-30s %s %s\n", ts, name, value, unit)
	}
}

// ── shared helpers ───────────────────────────────────────────────────────────

// advanceCursor returns the next cursor value from a set of rows.
// It finds the latest created_at, adds 1ms to avoid re-fetching the same row,
// and returns an RFC3339 string. If no valid timestamp is found, returns current.
func advanceCursor(rows []map[string]any, current string) string {
	ts := latestTimestamp(rows)
	if ts == "" {
		return current
	}
	t, err := time.Parse(time.RFC3339, ts)
	if err != nil {
		return current
	}
	return t.Add(time.Millisecond).UTC().Format(time.RFC3339)
}

// latestTimestamp returns the most recent created_at value from a set of rows.
func latestTimestamp(rows []map[string]any) string {
	var latest time.Time
	for _, row := range rows {
		raw := strings.TrimSpace(fmt.Sprint(row["created_at"]))
		if raw == "" {
			continue
		}
		t, err := time.Parse(time.RFC3339, raw)
		if err != nil {
			continue
		}
		if t.After(latest) {
			latest = t
		}
	}
	if latest.IsZero() {
		return ""
	}
	return latest.UTC().Format(time.RFC3339)
}

// computeErrorRate returns the percentage of ERROR+CRITICAL events out of total.
func computeErrorRate(bySeverity *telemetryAggregation, total int) float64 {
	if total == 0 || bySeverity == nil {
		return 0
	}
	errorCount := 0
	for _, b := range bySeverity.Buckets {
		switch strings.ToUpper(b.Key) {
		case "ERROR", "CRITICAL":
			errorCount += b.Count
		}
	}
	return float64(errorCount) / float64(total) * 100
}
