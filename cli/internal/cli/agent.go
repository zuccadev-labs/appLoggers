package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

type commandContract struct {
	Command     string   `json:"command"`
	Description string   `json:"description"`
	OutputModes []string `json:"output_modes"`
	Stable      bool     `json:"stable"`
}

type agentSchemaPayload struct {
	Name            string            `json:"name"`
	Version         string            `json:"version"`
	Recommendation  string            `json:"recommendation"`
	DefaultOutput   string            `json:"default_output"`
	ContractVersion string            `json:"contract_version"`
	EnvVars         []string          `json:"env_vars"`
	Commands        []commandContract `json:"commands"`
}

func newAgentCommand() *cobra.Command {
	agentCmd := &cobra.Command{
		Use:   "agent",
		Short: "Agent-focused commands",
	}

	agentCmd.AddCommand(&cobra.Command{
		Use:   "schema",
		Short: "Print execution schema for AI agents",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("agent schema does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			payload := agentSchemaPayload{
				Name:            "applogger-cli",
				Version:         buildVersion,
				Recommendation:  "Agents should prefer --output agent (TOON) and use --output json when strict JSON is required; when multiple telemetry apps exist, resolve the active project via --project, APPLOGGER_PROJECT, or workspace autodetection",
				DefaultOutput:   "text",
				ContractVersion: "1.0.0",
				EnvVars: []string{
					"APPLOGGER_CONFIG",
					"APPLOGGER_PROJECT",
					"appLogger_supabaseUrl",
					"appLogger_supabaseKey",
					"appLogger_supabaseSchema",
					"appLogger_supabaseLogTable",
					"appLogger_supabaseMetricTable",
					"appLogger_supabaseTimeoutSeconds",
				},
				Commands: []commandContract{
					{Command: "--syncbin-metadata", Description: "Metadata discovery endpoint", OutputModes: []string{"text", "json", "agent"}, Stable: true},
					{Command: "version", Description: "CLI build information", OutputModes: []string{"text", "json", "agent"}, Stable: true},
					{Command: "capabilities", Description: "Feature discovery endpoint", OutputModes: []string{"text", "json", "agent"}, Stable: true},
					{Command: "health", Description: "Readiness endpoint with resolved project context when available", OutputModes: []string{"text", "json", "agent"}, Stable: true},
					{Command: "telemetry query", Description: "Telemetry query command backed by Supabase with optional aggregation", OutputModes: []string{"text", "json", "agent"}, Stable: false},
					{Command: "telemetry agent-response", Description: "Compact TOON envelope dedicated to agent orchestration", OutputModes: []string{"agent"}, Stable: false},
				},
			}
			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), payload)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), payload)
			}

			_, err := fmt.Fprintf(cmd.OutOrStdout(), "name: %s\nversion: %s\ncontract_version: %s\n", payload.Name, payload.Version, payload.ContractVersion)
			if err != nil {
				return err
			}
			_, err = fmt.Fprintln(cmd.OutOrStdout(), "recommendation: use --output json for agent integrations")
			return err
		},
	})

	return agentCmd
}
