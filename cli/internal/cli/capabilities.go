package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

type capabilityEntry struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	Stability   string `json:"stability"`
}

type capabilitiesPayload struct {
	Name         string            `json:"name"`
	Version      string            `json:"version"`
	ConfigInputs []string          `json:"config_inputs,omitempty"`
	OutputModes  []string          `json:"output_modes"`
	ExitCodes    map[string]int    `json:"exit_codes"`
	Capabilities []capabilityEntry `json:"capabilities"`
}

func buildCapabilitiesPayload() capabilitiesPayload {
	return capabilitiesPayload{
		Name:         "apploggers",
		Version:      buildVersion,
		ConfigInputs: []string{"environment", "project_config", "workspace_autodetect"},
		OutputModes:  []string{"text", "json", "agent"},
		ExitCodes: map[string]int{
			"success":     exitCodeSuccess,
			"error":       exitCodeError,
			"usage_error": exitCodeUsage,
		},
		Capabilities: []capabilityEntry{
			{Name: "syncbin-metadata", Description: "Emit syncbin metadata envelope", Stability: "stable"},
			{Name: "version", Description: "Build and version introspection", Stability: "stable"},
			{Name: "upgrade", Description: "Self-update to latest release with SHA-256 checksum verification", Stability: "stable"},
			{Name: "capabilities", Description: "Machine-readable CLI capabilities", Stability: "stable"},
			{Name: "health", Description: "Runtime readiness probe", Stability: "stable"},
			{Name: "health --deep", Description: "Deep Supabase connectivity probe with latency measurement", Stability: "stable"},
			{Name: "agent schema", Description: "Schema and execution contract for agent clients (contract_version 2.0.0)", Stability: "stable"},
			{Name: "telemetry query", Description: "Full-featured telemetry query: environment, min-severity, extra-key/value, offset, order, throwable, sdk-version, retry on 429/503", Stability: "preview"},
			{Name: "telemetry agent-response", Description: "Compact TOON response optimized for agent orchestration", Stability: "preview"},
			{Name: "telemetry stream", Description: "SSE stream (text/event-stream) for frontend EventSource consumers", Stability: "preview"},
			{Name: "telemetry tail", Description: "Follow mode — prints new events as they arrive (tail -f equivalent), supports --output json", Stability: "preview"},
			{Name: "telemetry stats", Description: "Quick statistical summary: error rate, top tags, events per hour, by environment — supports all standard filters", Stability: "preview"},
		},
	}
}

func newCapabilitiesCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "capabilities",
		Short: "Print machine-readable CLI capabilities",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("capabilities does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}
			payload := buildCapabilitiesPayload()
			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), payload)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), payload)
			}

			_, err := fmt.Fprintf(cmd.OutOrStdout(), "name: %s\nversion: %s\n", payload.Name, payload.Version)
			if err != nil {
				return err
			}
			_, err = fmt.Fprintln(cmd.OutOrStdout(), "capabilities:")
			if err != nil {
				return err
			}
			for _, capability := range payload.Capabilities {
				if _, err = fmt.Fprintf(cmd.OutOrStdout(), "- %s (%s): %s\n", capability.Name, capability.Stability, capability.Description); err != nil {
					return err
				}
			}
			return nil
		},
	}
}
