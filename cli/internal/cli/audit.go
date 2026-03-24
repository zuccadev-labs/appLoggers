package cli

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

// ── audit privacy ─────────────────────────────────────────────────────────────

type privacyEventRow struct {
	ID          string `json:"id"`
	Level       string `json:"level"`
	UserID      string `json:"user_id"`
	DeviceID    string `json:"device_id"`
	Environment string `json:"environment"`
	Extra       any    `json:"extra"`
	CreatedAt   string `json:"created_at"`
}

type privacyReport struct {
	OK             bool                  `json:"ok"`
	Environment    string                `json:"environment,omitempty"`
	From           string                `json:"from"`
	To             string                `json:"to"`
	TotalEvents    int                   `json:"total_events"`
	WithUserID     int                   `json:"with_user_id"`
	WithDeviceID   int                   `json:"with_device_id"`
	PIIRisk        string                `json:"pii_risk"`
	ConsentBreakdown map[string]int      `json:"consent_breakdown"`
	UserIDPct      float64               `json:"user_id_pct"`
	DeviceIDPct    float64               `json:"device_id_pct"`
	Recommendations []string             `json:"recommendations,omitempty"`
}

func newAuditCommand() *cobra.Command {
	auditCmd := &cobra.Command{
		Use:   "audit",
		Short: "Audit and compliance operations",
	}
	auditCmd.AddCommand(newAuditPrivacyCommand())
	return auditCmd
}

func newAuditPrivacyCommand() *cobra.Command {
	var environment string
	var days int

	cmd := &cobra.Command{
		Use:   "privacy",
		Short: "Analyze recent logs for PII exposure and consent compliance",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("audit privacy does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			if days < 1 || days > 90 {
				return newUsageError("invalid --days value %d (expected 1..90)", days)
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			now := time.Now().UTC()
			from := now.Add(-time.Duration(days) * 24 * time.Hour).Format(time.RFC3339)
			to := now.Format(time.RFC3339)

			req := telemetryQueryRequest{
				Source:      "logs",
				From:        from,
				To:          to,
				Environment: environment,
				Limit:       1000,
				Order:       "desc",
			}

			ctx, cancel := context.WithTimeout(context.Background(), cfg.timeout())
			defer cancel()

			rows, err := doQueryWithRetry(ctx, cfg, req, 3)
			if err != nil {
				return err
			}

			report := buildPrivacyReport(rows, environment, from, to)

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), report)
			}
			return printPrivacyReport(cmd, report)
		},
	}

	cmd.Flags().StringVar(&environment, "environment", "", "Filter by environment (production|staging|development)")
	cmd.Flags().IntVar(&days, "days", 7, "Number of days to analyze (1..90)")
	return cmd
}

func buildPrivacyReport(rows []map[string]any, environment, from, to string) privacyReport {
	total := len(rows)
	withUserID := 0
	withDeviceID := 0
	consentBreakdown := map[string]int{"strict": 0, "performance": 0, "marketing": 0, "unknown": 0}

	for _, row := range rows {
		uid, _ := row["user_id"].(string)
		did, _ := row["device_id"].(string)
		level, _ := row["level"].(string)

		if strings.TrimSpace(uid) != "" {
			withUserID++
		}
		if strings.TrimSpace(did) != "" {
			withDeviceID++
		}
		// Infer consent from level
		switch strings.ToUpper(level) {
		case "CRITICAL", "ERROR":
			consentBreakdown["strict"]++
		case "WARN", "METRIC":
			consentBreakdown["performance"]++
		case "INFO", "DEBUG":
			consentBreakdown["marketing"]++
		default:
			consentBreakdown["unknown"]++
		}
	}

	userIDPct := 0.0
	deviceIDPct := 0.0
	if total > 0 {
		userIDPct = float64(withUserID) / float64(total) * 100
		deviceIDPct = float64(withDeviceID) / float64(total) * 100
	}

	risk := assessPIIRisk(userIDPct, deviceIDPct, total)
	recommendations := buildRecommendations(userIDPct, deviceIDPct, consentBreakdown, total)

	return privacyReport{
		OK:               true,
		Environment:      environment,
		From:             from,
		To:               to,
		TotalEvents:      total,
		WithUserID:       withUserID,
		WithDeviceID:     withDeviceID,
		PIIRisk:          risk,
		ConsentBreakdown: consentBreakdown,
		UserIDPct:        userIDPct,
		DeviceIDPct:      deviceIDPct,
		Recommendations:  recommendations,
	}
}

func assessPIIRisk(userIDPct, deviceIDPct float64, total int) string {
	if total == 0 {
		return "none"
	}
	if userIDPct > 80 {
		return "high"
	}
	if userIDPct > 20 || deviceIDPct > 90 {
		return "medium"
	}
	return "low"
}

func buildRecommendations(userIDPct, deviceIDPct float64, breakdown map[string]int, total int) []string {
	var recs []string
	if total == 0 {
		return recs
	}
	if userIDPct > 50 {
		recs = append(recs, "Over 50% of events contain user_id — verify GDPR consent is collected before setAnonymousUserId()")
	}
	if deviceIDPct > 95 {
		recs = append(recs, "Almost all events contain device_id — consider enabling dataMinimizationEnabled=true for STRICT consent users")
	}
	strictPct := float64(breakdown["strict"]) / float64(total) * 100
	if strictPct > 30 {
		recs = append(recs, fmt.Sprintf("%.0f%% of events are STRICT-level (errors/crashes) — ensure user_id is suppressed when consent=STRICT", strictPct))
	}
	return recs
}

func printPrivacyReport(cmd *cobra.Command, r privacyReport) error {
	_, err := fmt.Fprintf(cmd.OutOrStdout(),
		"privacy_audit:\n  from: %s\n  to: %s\n  environment: %s\n  total_events: %d\n  with_user_id: %d (%.1f%%)\n  with_device_id: %d (%.1f%%)\n  pii_risk: %s\n  consent_breakdown:\n    strict: %d\n    performance: %d\n    marketing: %d\n",
		r.From, r.To, r.Environment,
		r.TotalEvents,
		r.WithUserID, r.UserIDPct,
		r.WithDeviceID, r.DeviceIDPct,
		r.PIIRisk,
		r.ConsentBreakdown["strict"],
		r.ConsentBreakdown["performance"],
		r.ConsentBreakdown["marketing"],
	)
	if err != nil {
		return err
	}
	for _, rec := range r.Recommendations {
		if _, e := fmt.Fprintf(cmd.OutOrStdout(), "  recommendation: %s\n", rec); e != nil {
			return e
		}
	}
	return nil
}
