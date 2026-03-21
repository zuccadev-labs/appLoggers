package cli

import (
	"fmt"
	"time"

	"github.com/spf13/cobra"
)

type healthPayload struct {
	OK        bool   `json:"ok"`
	Status    string `json:"status"`
	Version   string `json:"version"`
	Project   string `json:"project,omitempty"`
	ConfigSource string `json:"config_source,omitempty"`
	Timestamp string `json:"timestamp"`
}

func newHealthCommand() *cobra.Command {
	return &cobra.Command{
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
			if cfg, err := loadSupabaseConfig(); err == nil {
				payload.Project = cfg.Project
				payload.ConfigSource = cfg.ConfigSource
			}
			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), payload)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), payload)
			}

			_, err := fmt.Fprintf(cmd.OutOrStdout(), "status: %s\nversion: %s\n", payload.Status, payload.Version)
			return err
		},
	}
}
