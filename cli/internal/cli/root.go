package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"

	"github.com/spf13/cobra"
)

var (
	outputFormat    string
	verbose         bool
	syncbinMetadata bool
)

var rootCmd = &cobra.Command{
	Use:           "applogger-cli",
	Short:         "AppLoggers telemetry CLI",
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) > 0 {
			return newUsageError("unexpected arguments: %v", args)
		}
		if err := validateOutputFormat(outputFormat); err != nil {
			return err
		}
		if syncbinMetadata {
			return writeMetadata(cmd)
		}
		return cmd.Help()
	},
}

func Execute() int {
	if err := rootCmd.Execute(); err != nil {
		var usageErr usageError
		exitCode := exitCodeError
		kind := "runtime_error"
		if errors.As(err, &usageErr) {
			exitCode = exitCodeUsage
			kind = "usage_error"
		}

		if outputFormat == "json" || outputFormat == "agent" {
			errPayload := map[string]any{
				"ok":         false,
				"error":      err.Error(),
				"error_kind": kind,
				"exit_code":  exitCode,
			}
			if outputFormat == "json" {
				_ = writeJSON(os.Stderr, errPayload)
			} else {
				_ = writeAgent(cmdStderrWriter{}, errPayload)
			}
		} else {
			fmt.Fprintln(os.Stderr, err)
		}
		return exitCode
	}
	return exitCodeSuccess
}

func init() {
	rootCmd.SetFlagErrorFunc(func(cmd *cobra.Command, err error) error {
		return usageError{msg: err.Error()}
	})

	rootCmd.PersistentFlags().StringVar(&outputFormat, "output", "text", "Output format: text|json|agent")
	rootCmd.PersistentFlags().BoolVarP(&verbose, "verbose", "v", false, "Enable verbose output")
	rootCmd.Flags().BoolVar(&syncbinMetadata, "syncbin-metadata", false, "Print Syncbin metadata")
	_ = rootCmd.Flags().MarkHidden("syncbin-metadata")

	rootCmd.AddCommand(newVersionCommand())
	rootCmd.AddCommand(newCapabilitiesCommand())
	rootCmd.AddCommand(newHealthCommand())
	rootCmd.AddCommand(newAgentCommand())
	rootCmd.AddCommand(newTelemetryCommand())
}

func validateOutputFormat(format string) error {
	switch format {
	case "text", "json", "agent":
		return nil
	default:
		return newUsageError("invalid --output value %q (expected text|json|agent)", format)
	}
}

type cmdStderrWriter struct{}

func (cmdStderrWriter) Write(p []byte) (int, error) {
	return os.Stderr.Write(p)
}

func writeJSON(out io.Writer, payload any) error {
	encoder := json.NewEncoder(out)
	encoder.SetIndent("", "  ")
	return encoder.Encode(payload)
}
