package cli

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"

	"github.com/spf13/cobra"
)

const remoteConfigTable = "device_remote_config"

func newRemoteConfigCommand() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "remote-config",
		Short: "Manage remote device configuration rules",
	}

	cmd.AddCommand(newRemoteConfigListCommand())
	cmd.AddCommand(newRemoteConfigSetCommand())
	cmd.AddCommand(newRemoteConfigDeleteCommand())

	return cmd
}

// ── list ─────────────────────────────────────────────────────────────────────

func newRemoteConfigListCommand() *cobra.Command {
	var environment string
	var fingerprint string

	cmd := &cobra.Command{
		Use:   "list",
		Short: "List remote config rules",
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}
			return runRemoteConfigList(cmd.Context(), cfg, environment, fingerprint, cmd.OutOrStdout())
		},
	}

	cmd.Flags().StringVar(&environment, "environment", "", "Filter by environment")
	cmd.Flags().StringVar(&fingerprint, "fingerprint", "", "Filter by device fingerprint")

	return cmd
}

func runRemoteConfigList(ctx context.Context, cfg supabaseConfig, environment, fingerprint string, out io.Writer) error {
	client := supabaseHTTPClient(cfg)

	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", remoteConfigTable)
	q := base.Query()
	q.Set("select", "*")
	q.Set("order", "created_at.desc")
	if environment != "" {
		q.Set("environment", "eq."+environment)
	}
	if fingerprint != "" {
		q.Set("device_fingerprint", "eq."+fingerprint)
	}
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base.String(), nil)
	if err != nil {
		return fmt.Errorf("building request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("supabase error %d: %s", resp.StatusCode, string(body))
	}

	var rows []map[string]any
	if err := json.Unmarshal(body, &rows); err != nil {
		return fmt.Errorf("parsing response: %w", err)
	}

	switch outputFormat {
	case "json":
		return writeJSON(out, map[string]any{
			"ok":    true,
			"count": len(rows),
			"rules": rows,
		})
	case "agent":
		return writeAgent(out, map[string]any{
			"ok":    true,
			"count": len(rows),
			"rules": rows,
		})
	case "csv":
		return writeCSV(out, rows)
	default:
		if len(rows) == 0 {
			_, err := fmt.Fprintln(out, "No remote config rules found.")
			return err
		}
		for _, row := range rows {
			fp, _ := row["device_fingerprint"].(string)
			if fp == "" {
				fp = "(global)"
			}
			env, _ := row["environment"].(string)
			if env == "" {
				env = "(all)"
			}
			enabled, _ := row["enabled"].(bool)
			id, _ := row["id"].(string)
			minLevel, _ := row["min_level"].(string)
			debugEnabled, _ := row["debug_enabled"].(bool)
			samplingRate, _ := row["sampling_rate"].(float64)
			notes, _ := row["notes"].(string)

			status := "enabled"
			if !enabled {
				status = "disabled"
			}

			if _, err := fmt.Fprintf(out, "  [%s] %s\n", status, id); err != nil {
				return err
			}
			if _, err := fmt.Fprintf(out, "    fingerprint: %s  environment: %s\n", fp, env); err != nil {
				return err
			}
			if _, err := fmt.Fprintf(out, "    min_level: %s  debug: %v  sampling: %.2f\n", minLevel, debugEnabled, samplingRate); err != nil {
				return err
			}
			if tagsAllow, ok := row["tags_allow"]; ok && tagsAllow != nil {
				if _, err := fmt.Fprintf(out, "    tags_allow: %v\n", tagsAllow); err != nil {
					return err
				}
			}
			if tagsBlock, ok := row["tags_block"]; ok && tagsBlock != nil {
				if _, err := fmt.Fprintf(out, "    tags_block: %v\n", tagsBlock); err != nil {
					return err
				}
			}
			if notes != "" {
				if _, err := fmt.Fprintf(out, "    notes: %s\n", notes); err != nil {
					return err
				}
			}
			if _, err := fmt.Fprintln(out); err != nil {
				return err
			}
		}
		_, err := fmt.Fprintf(out, "Total: %d rule(s)\n", len(rows))
		return err
	}
}

// ── set ──────────────────────────────────────────────────────────────────────

func newRemoteConfigSetCommand() *cobra.Command {
	var fingerprint string
	var environment string
	var minLevel string
	var debugEnabled string
	var samplingRate float64
	var tagsAllow string
	var tagsBlock string
	var notes string
	var enabled bool

	cmd := &cobra.Command{
		Use:   "set",
		Short: "Create or update a remote config rule",
		Long: `Create or update a remote config rule for a specific device or globally.

Examples:
  # Global: set min_level=ERROR for all devices
  apploggers remote-config set --min-level ERROR

  # Per-device: enable debug for a specific device
  apploggers remote-config set --fingerprint abc123 --debug true

  # Tag filtering: only capture Auth and Network tags
  apploggers remote-config set --fingerprint abc123 --tags-allow "Auth,Network"

  # Sampling: capture 10% of events globally
  apploggers remote-config set --sampling-rate 0.1`,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			payload := map[string]any{
				"enabled": enabled,
			}
			if fingerprint != "" {
				payload["device_fingerprint"] = fingerprint
			}
			if environment != "" {
				payload["environment"] = environment
			}
			if minLevel != "" {
				valid := map[string]bool{"DEBUG": true, "INFO": true, "WARN": true, "ERROR": true, "CRITICAL": true}
				upper := strings.ToUpper(minLevel)
				if !valid[upper] {
					return newUsageError("invalid --min-level %q (expected DEBUG|INFO|WARN|ERROR|CRITICAL)", minLevel)
				}
				payload["min_level"] = upper
			}
			if debugEnabled != "" {
				switch strings.ToLower(debugEnabled) {
				case "true":
					payload["debug_enabled"] = true
				case "false":
					payload["debug_enabled"] = false
				default:
					return newUsageError("invalid --debug %q (expected true|false)", debugEnabled)
				}
			}
			if cmd.Flags().Changed("sampling-rate") {
				if samplingRate < 0 || samplingRate > 1 {
					return newUsageError("--sampling-rate must be between 0.0 and 1.0")
				}
				payload["sampling_rate"] = samplingRate
			}
			if tagsAllow != "" {
				payload["tags_allow"] = splitCSV(tagsAllow)
			}
			if tagsBlock != "" {
				payload["tags_block"] = splitCSV(tagsBlock)
			}
			if notes != "" {
				payload["notes"] = notes
			}

			return runRemoteConfigSet(cmd.Context(), cfg, payload, cmd.OutOrStdout())
		},
	}

	cmd.Flags().StringVar(&fingerprint, "fingerprint", "", "Device fingerprint (omit for global rule)")
	cmd.Flags().StringVar(&environment, "environment", "", "Environment filter (e.g. production, staging)")
	cmd.Flags().StringVar(&minLevel, "min-level", "", "Minimum log level: DEBUG|INFO|WARN|ERROR|CRITICAL")
	cmd.Flags().StringVar(&debugEnabled, "debug", "", "Enable debug mode: true|false")
	cmd.Flags().Float64Var(&samplingRate, "sampling-rate", 1.0, "Sampling rate 0.0-1.0 (1.0 = keep all)")
	cmd.Flags().StringVar(&tagsAllow, "tags-allow", "", "Comma-separated allowlist of tags")
	cmd.Flags().StringVar(&tagsBlock, "tags-block", "", "Comma-separated blocklist of tags")
	cmd.Flags().StringVar(&notes, "notes", "", "Admin notes (e.g. 'Debug user issue #123')")
	cmd.Flags().BoolVar(&enabled, "enabled", true, "Enable or disable this rule")

	return cmd
}

func runRemoteConfigSet(ctx context.Context, cfg supabaseConfig, payload map[string]any, out io.Writer) error {
	client := supabaseHTTPClient(cfg)

	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", remoteConfigTable)

	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshalling payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base.String(), bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("building request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Prefer", "return=representation")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("supabase error %d: %s", resp.StatusCode, string(respBody))
	}

	var created []map[string]any
	if err := json.Unmarshal(respBody, &created); err != nil {
		return fmt.Errorf("parsing response: %w", err)
	}

	switch outputFormat {
	case "json":
		return writeJSON(out, map[string]any{
			"ok":   true,
			"rule": created,
		})
	case "agent":
		return writeAgent(out, map[string]any{
			"ok":   true,
			"rule": created,
		})
	default:
		if len(created) > 0 {
			id, _ := created[0]["id"].(string)
			_, err := fmt.Fprintf(out, "Remote config rule created: %s\n", id)
			return err
		}
		_, err := fmt.Fprintln(out, "Remote config rule created.")
		return err
	}
}

// ── delete ───────────────────────────────────────────────────────────────────

func newRemoteConfigDeleteCommand() *cobra.Command {
	var ruleID string
	var fingerprint string

	cmd := &cobra.Command{
		Use:   "delete",
		Short: "Delete a remote config rule by ID or fingerprint",
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			if ruleID == "" && fingerprint == "" {
				return newUsageError("provide --id or --fingerprint to identify the rule to delete")
			}
			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}
			return runRemoteConfigDelete(cmd.Context(), cfg, ruleID, fingerprint, cmd.OutOrStdout())
		},
	}

	cmd.Flags().StringVar(&ruleID, "id", "", "Rule UUID to delete")
	cmd.Flags().StringVar(&fingerprint, "fingerprint", "", "Delete all rules for this device fingerprint")

	return cmd
}

func runRemoteConfigDelete(ctx context.Context, cfg supabaseConfig, ruleID, fingerprint string, out io.Writer) error {
	client := supabaseHTTPClient(cfg)

	base, err := url.Parse(strings.TrimSpace(cfg.URL))
	if err != nil {
		return fmt.Errorf("invalid Supabase URL: %w", err)
	}
	base.Path = path.Join(base.Path, "rest", "v1", remoteConfigTable)
	q := base.Query()
	if ruleID != "" {
		q.Set("id", "eq."+ruleID)
	} else {
		q.Set("device_fingerprint", "eq."+fingerprint)
	}
	base.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, base.String(), nil)
	if err != nil {
		return fmt.Errorf("building request: %w", err)
	}
	req.Header.Set("apikey", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+cfg.APIKey)
	req.Header.Set("Prefer", "return=representation")
	req.Header.Set("Accept", "application/json")
	if cfg.Schema != "" {
		req.Header.Set("Content-Profile", cfg.Schema)
		req.Header.Set("Accept-Profile", cfg.Schema)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("supabase error %d: %s", resp.StatusCode, string(respBody))
	}

	var deleted []map[string]any
	if err := json.Unmarshal(respBody, &deleted); err != nil {
		return fmt.Errorf("parsing response: %w", err)
	}

	switch outputFormat {
	case "json":
		return writeJSON(out, map[string]any{
			"ok":      true,
			"deleted": len(deleted),
		})
	case "agent":
		return writeAgent(out, map[string]any{
			"ok":      true,
			"deleted": len(deleted),
		})
	default:
		_, err := fmt.Fprintf(out, "Deleted %d remote config rule(s).\n", len(deleted))
		return err
	}
}

func splitCSV(s string) []string {
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
}
