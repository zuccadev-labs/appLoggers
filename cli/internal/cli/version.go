package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

func newVersionCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "Print CLI version information",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("version does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			payload := map[string]string{
				"name":    "applogger-cli",
				"version": buildVersion,
				"commit":  buildCommit,
				"date":    buildDate,
			}

			if outputFormat == "json" {
				return writeJSON(cmd.OutOrStdout(), payload)
			}
			if outputFormat == "agent" {
				return writeAgent(cmd.OutOrStdout(), payload)
			}

			_, err := fmt.Fprintf(
				cmd.OutOrStdout(),
				"applogger-cli version %s\ncommit: %s\nbuilt: %s\n",
				buildVersion,
				buildCommit,
				buildDate,
			)
			return err
		},
	}
}
