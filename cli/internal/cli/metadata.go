package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

type metadataPayload struct {
	Name        string `json:"name"`
	Version     string `json:"version"`
	Description string `json:"description"`
}

func writeMetadata(cmd *cobra.Command) error {
	payload := metadataPayload{
		Name:        "apploggers",
		Version:     buildVersion,
		Description: "CLI for querying and analyzing AppLoggers telemetry",
	}

	if outputFormat == "json" {
		return writeJSON(cmd.OutOrStdout(), payload)
	}
	if outputFormat == "agent" {
		return writeAgent(cmd.OutOrStdout(), payload)
	}

	_, err := fmt.Fprintf(
		cmd.OutOrStdout(),
		"name: %s\nversion: %s\ndescription: %s\n",
		payload.Name,
		payload.Version,
		payload.Description,
	)
	return err
}
