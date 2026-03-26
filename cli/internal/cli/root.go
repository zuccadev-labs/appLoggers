package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"sort"
	"strings"

	"github.com/spf13/cobra"
)

var (
	outputFormat    string
	verbose         bool
	syncbinMetadata bool
	projectName     string
	configFilePath  string
)

var rootCmd = &cobra.Command{
	Use:           "apploggers",
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
	rootCmd.PersistentFlags().StringVar(&projectName, "project", "", "Project profile to use from the AppLogger CLI config")
	rootCmd.PersistentFlags().StringVar(&configFilePath, "config", "", "Path to the AppLogger CLI project config file")
	rootCmd.Flags().BoolVar(&syncbinMetadata, "syncbin-metadata", false, "Print Syncbin metadata")
	_ = rootCmd.Flags().MarkHidden("syncbin-metadata")

	rootCmd.AddCommand(newVersionCommand())
	rootCmd.AddCommand(newUpgradeCommand())
	rootCmd.AddCommand(newCapabilitiesCommand())
	rootCmd.AddCommand(newHealthCommand())
	rootCmd.AddCommand(newAgentCommand())
	rootCmd.AddCommand(newTelemetryCommand())
	rootCmd.AddCommand(newAuditCommand())
	rootCmd.AddCommand(newEraseCommand())
	rootCmd.AddCommand(newExplainCommand())
	rootCmd.AddCommand(newServeCommand())
	rootCmd.AddCommand(newVerifyCommand())
	rootCmd.AddCommand(newRemoteConfigCommand())
}

func validateOutputFormat(format string) error {
	switch format {
	case "text", "json", "agent", "csv":
		return nil
	default:
		return newUsageError("invalid --output value %q (expected text|json|agent|csv)", format)
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

// writeCSV writes a slice of maps as CSV with automatic header detection.
func writeCSV(out io.Writer, rows []map[string]any) error {
	if len(rows) == 0 {
		_, err := fmt.Fprintln(out, "(no rows)")
		return err
	}

	// Collect all unique keys for header (deterministic order)
	keySet := map[string]bool{}
	for _, row := range rows {
		for k := range row {
			keySet[k] = true
		}
	}
	headers := make([]string, 0, len(keySet))
	for k := range keySet {
		headers = append(headers, k)
	}
	sort.Strings(headers)

	// Write header
	if _, err := fmt.Fprintln(out, strings.Join(headers, ",")); err != nil {
		return err
	}

	// Write rows
	for _, row := range rows {
		vals := make([]string, len(headers))
		for i, h := range headers {
			v, ok := row[h]
			if !ok || v == nil {
				vals[i] = ""
				continue
			}
			s := fmt.Sprintf("%v", v)
			// Escape CSV: quote if contains comma, newline, or double-quote
			if strings.ContainsAny(s, ",\"\n") {
				s = "\"" + strings.ReplaceAll(s, "\"", "\"\"") + "\""
			}
			vals[i] = s
		}
		if _, err := fmt.Fprintln(out, strings.Join(vals, ",")); err != nil {
			return err
		}
	}
	return nil
}
