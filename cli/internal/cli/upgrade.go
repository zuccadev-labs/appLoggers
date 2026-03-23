package cli

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"runtime"
	"strings"
	"time"

	update "github.com/inconshreveable/go-update"
	"github.com/spf13/cobra"
)

const (
	cliReleaseRepo   = "zuccadev-labs/appLoggers"
	cliReleasePrefix = "apploggers-v"
)

type githubRelease struct {
	TagName string `json:"tag_name"`
}

type upgradePayload struct {
	OK             bool   `json:"ok"`
	Updated        bool   `json:"updated"`
	CurrentVersion string `json:"current_version"`
	TargetVersion  string `json:"target_version"`
	Asset          string `json:"asset"`
	Message        string `json:"message"`
}

func newUpgradeCommand() *cobra.Command {
	var requestedVersion string
	var force bool

	cmd := &cobra.Command{
		Use:   "upgrade",
		Short: "Upgrade apploggers to the latest published release",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("upgrade does not accept positional arguments")
			}
			if err := validateOutputFormat(outputFormat); err != nil {
				return err
			}

			targetVersion, err := resolveUpgradeTargetVersion(strings.TrimSpace(requestedVersion))
			if err != nil {
				return err
			}

			assetName, err := resolveUpgradeAssetName()
			if err != nil {
				return err
			}

			currentVersion := strings.TrimSpace(buildVersion)
			if currentVersion == "" {
				currentVersion = "dev"
			}

			if !force && currentVersion == targetVersion {
				payload := upgradePayload{
					OK:             true,
					Updated:        false,
					CurrentVersion: currentVersion,
					TargetVersion:  targetVersion,
					Asset:          assetName,
					Message:        "apploggers is already up to date",
				}
				return writeUpgradeOutput(cmd, payload)
			}

			releaseBase := fmt.Sprintf("https://github.com/%s/releases/download/%s", cliReleaseRepo, targetVersion)
			assetURL := fmt.Sprintf("%s/%s", releaseBase, assetName)
			checksumURL := fmt.Sprintf("%s/%s.sha256", releaseBase, assetName)

			assetBytes, err := downloadURL(assetURL)
			if err != nil {
				return fmt.Errorf("failed to download release asset %s: %w", assetURL, err)
			}
			checksumText, err := downloadURL(checksumURL)
			if err != nil {
				return fmt.Errorf("failed to download checksum %s: %w", checksumURL, err)
			}

			expectedChecksum, err := parseChecksum(string(checksumText))
			if err != nil {
				return fmt.Errorf("invalid checksum file for %s: %w", assetName, err)
			}
			actualChecksum := sha256.Sum256(assetBytes)
			if expectedChecksum != actualChecksum {
				return fmt.Errorf("checksum mismatch for %s", assetName)
			}

			if err := update.Apply(bytes.NewReader(assetBytes), update.Options{}); err != nil {
				if rollbackErr := update.RollbackError(err); rollbackErr != nil {
					return fmt.Errorf("upgrade failed and rollback failed: %v (rollback error: %w)", err, rollbackErr)
				}
				return fmt.Errorf("upgrade failed: %w", err)
			}

			payload := upgradePayload{
				OK:             true,
				Updated:        true,
				CurrentVersion: currentVersion,
				TargetVersion:  targetVersion,
				Asset:          assetName,
				Message:        "upgrade applied successfully; run 'apploggers version' to verify current binary",
			}
			return writeUpgradeOutput(cmd, payload)
		},
	}

	cmd.Flags().StringVar(&requestedVersion, "version", "", "Specific release tag to install (e.g. apploggers-v0.1.3)")
	cmd.Flags().BoolVar(&force, "force", false, "Force upgrade even if current version matches")
	return cmd
}

func writeUpgradeOutput(cmd *cobra.Command, payload upgradePayload) error {
	if outputFormat == "json" {
		return writeJSON(cmd.OutOrStdout(), payload)
	}
	if outputFormat == "agent" {
		return writeAgent(cmd.OutOrStdout(), payload)
	}

	if payload.Updated {
		_, err := fmt.Fprintf(
			cmd.OutOrStdout(),
			"upgrade successful\ncurrent: %s\ntarget: %s\nasset: %s\n",
			payload.CurrentVersion,
			payload.TargetVersion,
			payload.Asset,
		)
		return err
	}

	_, err := fmt.Fprintf(
		cmd.OutOrStdout(),
		"already up to date\ncurrent: %s\ntarget: %s\nasset: %s\n",
		payload.CurrentVersion,
		payload.TargetVersion,
		payload.Asset,
	)
	return err
}

func resolveUpgradeTargetVersion(requestedVersion string) (string, error) {
	if requestedVersion != "" {
		if !strings.HasPrefix(requestedVersion, cliReleasePrefix) {
			return "", newUsageError("--version must match %s*", cliReleasePrefix)
		}
		return requestedVersion, nil
	}

	apiURL := fmt.Sprintf("https://api.github.com/repos/%s/releases?per_page=100", cliReleaseRepo)
	body, err := downloadURL(apiURL)
	if err != nil {
		return "", fmt.Errorf("failed to resolve latest release: %w", err)
	}

	var releases []githubRelease
	if err := json.Unmarshal(body, &releases); err != nil {
		return "", fmt.Errorf("failed to parse releases response: %w", err)
	}
	for _, release := range releases {
		tag := strings.TrimSpace(release.TagName)
		if strings.HasPrefix(tag, cliReleasePrefix) {
			return tag, nil
		}
	}

	return "", fmt.Errorf("no release found matching %s*", cliReleasePrefix)
}

func resolveUpgradeAssetName() (string, error) {
	var osPart string
	switch runtime.GOOS {
	case "linux":
		osPart = "linux"
	case "darwin":
		osPart = "darwin"
	case "windows":
		osPart = "windows"
	default:
		return "", fmt.Errorf("unsupported operating system: %s", runtime.GOOS)
	}

	var archPart string
	switch runtime.GOARCH {
	case "amd64":
		archPart = "amd64"
	case "arm64":
		if runtime.GOOS == "windows" {
			return "", fmt.Errorf("unsupported architecture for windows releases: %s", runtime.GOARCH)
		}
		archPart = "arm64"
	default:
		return "", fmt.Errorf("unsupported architecture: %s", runtime.GOARCH)
	}

	asset := fmt.Sprintf("apploggers-%s-%s", osPart, archPart)
	if runtime.GOOS == "windows" {
		asset += ".exe"
	}
	return asset, nil
}

func parseChecksum(raw string) ([32]byte, error) {
	parts := strings.Fields(strings.TrimSpace(raw))
	if len(parts) == 0 {
		return [32]byte{}, fmt.Errorf("checksum payload is empty")
	}

	decoded, err := hex.DecodeString(parts[0])
	if err != nil {
		return [32]byte{}, fmt.Errorf("checksum is not valid hex: %w", err)
	}
	if len(decoded) != sha256.Size {
		return [32]byte{}, fmt.Errorf("checksum length is %d bytes, expected %d", len(decoded), sha256.Size)
	}

	var checksum [32]byte
	copy(checksum[:], decoded)
	return checksum, nil
}

func downloadURL(url string) ([]byte, error) {
	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Get(url) // #nosec G107 -- URL is controlled by release repository constants or validated tag input
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return nil, fmt.Errorf("http %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	return io.ReadAll(io.LimitReader(resp.Body, 32*1024*1024)) // 32 MB max
}
