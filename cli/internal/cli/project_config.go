package cli

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

type cliProjectFile struct {
	DefaultProject string              `json:"default_project"`
	Projects       []cliProjectProfile `json:"projects"`
}

type cliProjectProfile struct {
	Name           string                    `json:"name"`
	DisplayName    string                    `json:"display_name,omitempty"`
	WorkspaceRoots []string                  `json:"workspace_roots,omitempty"`
	Supabase       cliProjectSupabaseProfile `json:"supabase"`
}

type cliProjectSupabaseProfile struct {
	URL            string `json:"url"`
	APIKey         string `json:"api_key,omitempty"`
	APIKeyEnv      string `json:"api_key_env,omitempty"`
	Schema         string `json:"schema,omitempty"`
	LogsTable      string `json:"logs_table,omitempty"`
	MetricsTable   string `json:"metrics_table,omitempty"`
	TimeoutSeconds int    `json:"timeout_seconds,omitempty"`
}

func loadSupabaseConfigFromProjectConfig() (supabaseConfig, error) {
	configPath, explicitConfigPath := resolveProjectConfigPath()
	explicitProject := activeProjectName()

	if configPath == "" {
		if explicitProject != "" {
			return supabaseConfig{}, fmt.Errorf("project %q requested but no project config path is available; set --config or APPLOGGER_CONFIG", explicitProject)
		}
		return supabaseConfig{}, os.ErrNotExist
	}

	_, statErr := os.Stat(configPath)
	if statErr != nil {
		if os.IsNotExist(statErr) && !explicitConfigPath {
			if legacyPath := legacyProjectConfigPath(); legacyPath != "" {
				if _, legacyErr := os.Stat(legacyPath); legacyErr == nil {
					configPath = legacyPath
					statErr = nil
				} else if !os.IsNotExist(legacyErr) {
					return supabaseConfig{}, fmt.Errorf("failed to stat legacy project config %s: %w", legacyPath, legacyErr)
				}
			}
		}
		if statErr != nil {
			if os.IsNotExist(statErr) && !explicitConfigPath && explicitProject == "" {
				return supabaseConfig{}, os.ErrNotExist
			}
			if os.IsNotExist(statErr) {
				return supabaseConfig{}, fmt.Errorf("project config file not found: %s", configPath)
			}
			return supabaseConfig{}, fmt.Errorf("failed to stat project config %s: %w", configPath, statErr)
		}
	}

	content, err := os.ReadFile(configPath)
	if err != nil {
		return supabaseConfig{}, fmt.Errorf("failed to read project config %s: %w", configPath, err)
	}

	var file cliProjectFile
	if err := json.Unmarshal(content, &file); err != nil {
		return supabaseConfig{}, fmt.Errorf("invalid project config %s: %w", configPath, err)
	}
	if len(file.Projects) == 0 {
		return supabaseConfig{}, fmt.Errorf("project config %s does not define any projects", configPath)
	}

	selected, err := selectProjectProfile(file, explicitProject)
	if err != nil {
		return supabaseConfig{}, fmt.Errorf("failed to resolve AppLogger project from %s: %w", configPath, err)
	}

	apiKey := resolveProjectAPIKey(selected, configPath)
	if apiKey == "" {
		envName := strings.TrimSpace(selected.Supabase.APIKeyEnv)
		if envName != "" {
			return supabaseConfig{}, fmt.Errorf(
				"project %q: api_key_env is set to %q but the environment variable is empty or not exported; "+
					"export %s=<service_role_key> or set supabase.api_key directly in %s",
				selected.Name, envName, envName, configPath,
			)
		}
		return supabaseConfig{}, fmt.Errorf(
			"project %q is missing a Supabase API key; set supabase.api_key or supabase.api_key_env in %s",
			selected.Name, configPath,
		)
	}

	timeoutSeconds := selected.Supabase.TimeoutSeconds
	if timeoutSeconds == 0 {
		timeoutSeconds = 15
	}

	cfg := supabaseConfig{
		Project:        selected.Name,
		ConfigSource:   "project_config",
		URL:            strings.TrimSpace(selected.Supabase.URL),
		APIKey:         apiKey,
		Schema:         strings.TrimSpace(selected.Supabase.Schema),
		LogsTable:      strings.TrimSpace(selected.Supabase.LogsTable),
		MetricsTable:   strings.TrimSpace(selected.Supabase.MetricsTable),
		TimeoutSeconds: timeoutSeconds,
	}

	if cfg.URL == "" {
		return cfg, fmt.Errorf("project %q is missing supabase.url in %s", selected.Name, configPath)
	}
	if cfg.Schema == "" {
		cfg.Schema = "public"
	}
	if cfg.LogsTable == "" {
		cfg.LogsTable = "app_logs"
	}
	if cfg.MetricsTable == "" {
		cfg.MetricsTable = "app_metrics"
	}
	if cfg.TimeoutSeconds < 1 || cfg.TimeoutSeconds > 120 {
		return cfg, fmt.Errorf("project %q has invalid timeout %d in %s (expected 1..120)", selected.Name, cfg.TimeoutSeconds, configPath)
	}

	return validateSupabaseConfig(cfg)
}

func resolveProjectConfigPath() (string, bool) {
	if value := strings.TrimSpace(configFilePath); value != "" {
		return value, true
	}
	if value := strings.TrimSpace(os.Getenv("APPLOGGER_CONFIG")); value != "" {
		return value, true
	}
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return "", false
	}
	configDir := filepath.Join(homeDir, ".apploggers")
	if err := ensureConfigDir(configDir); err != nil {
		return "", false
	}
	configPath := filepath.Join(configDir, "cli.json")
	writeExampleConfigIfAbsent(configPath)
	return configPath, false
}

func ensureConfigDir(path string) error {
	info, err := os.Stat(path)
	if err == nil {
		if !info.IsDir() {
			return fmt.Errorf("config path %s exists but is not a directory", path)
		}
		return nil
	}
	if !os.IsNotExist(err) {
		return err
	}
	if err := os.MkdirAll(path, 0755); err != nil {
		return fmt.Errorf("failed to create config directory %s: %w", path, err)
	}
	return nil
}

const exampleConfigJSON = `{
  "_comment": "AppLoggers CLI — archivo de configuración de proyectos.",
  "_docs": "https://github.com/zuccadev-labs/appLoggers/tree/main/docs/ES/cli",
  "_key_resolution": "api_key_env tiene prioridad si la variable está exportada; si no, se usa api_key. Ambos pueden coexistir.",
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "_api_key_note": "Opción A: pon el valor directo del service_role key en api_key (no versionar). Opción B: pon el NOMBRE de la variable de entorno en api_key_env y exporta esa variable.",
        "api_key": "",
        "api_key_env": "APPLOGGER_SUPABASE_KEY"
      }
    }
  ]
}
`

func writeExampleConfigIfAbsent(configPath string) {
	if _, err := os.Stat(configPath); err == nil {
		return
	}
	_ = os.WriteFile(configPath, []byte(exampleConfigJSON), 0644)
}

func resolveProjectAPIKey(profile cliProjectProfile, configPath string) string {
	// Priority:
	// 1. api_key_env — if set and the env var is non-empty, use it.
	// 2. api_key    — direct value in the json (fallback when env var is absent/empty).
	// Both empty → caller reports the error with actionable guidance.
	if envName := strings.TrimSpace(profile.Supabase.APIKeyEnv); envName != "" {
		if value := strings.TrimSpace(os.Getenv(envName)); value != "" {
			return value
		}
		// env var is empty or not exported — fall through to api_key
	}
	return strings.TrimSpace(profile.Supabase.APIKey)
}

func legacyProjectConfigPath() string {
	baseDir, err := os.UserConfigDir()
	if err != nil {
		return ""
	}
	return filepath.Join(baseDir, "applogger", "cli.json")
}

func activeProjectName() string {
	if value := strings.TrimSpace(projectName); value != "" {
		return value
	}
	return strings.TrimSpace(os.Getenv("APPLOGGER_PROJECT"))
}

func selectProjectProfile(file cliProjectFile, explicitProject string) (cliProjectProfile, error) {
	if explicitProject != "" {
		for _, candidate := range file.Projects {
			if strings.EqualFold(strings.TrimSpace(candidate.Name), explicitProject) {
				return candidate, nil
			}
		}
		return cliProjectProfile{}, fmt.Errorf("project %q was not found; available projects: %s", explicitProject, joinProjectNames(file.Projects))
	}

	if cwd, err := os.Getwd(); err == nil {
		matches := make([]cliProjectProfile, 0)
		for _, candidate := range file.Projects {
			if projectMatchesWorkspace(candidate, cwd) {
				matches = append(matches, candidate)
			}
		}
		if len(matches) == 1 {
			return matches[0], nil
		}
		if len(matches) > 1 {
			return cliProjectProfile{}, fmt.Errorf("multiple projects match current workspace %q; use --project or APPLOGGER_PROJECT (%s)", cwd, joinProjectNames(matches))
		}
	}

	if defaultProject := strings.TrimSpace(file.DefaultProject); defaultProject != "" {
		for _, candidate := range file.Projects {
			if strings.EqualFold(strings.TrimSpace(candidate.Name), defaultProject) {
				return candidate, nil
			}
		}
		return cliProjectProfile{}, fmt.Errorf("default_project %q was not found in configured projects", defaultProject)
	}

	if len(file.Projects) == 1 {
		return file.Projects[0], nil
	}

	return cliProjectProfile{}, fmt.Errorf("multiple projects are configured and none matched the current workspace; use --project or APPLOGGER_PROJECT (%s)", joinProjectNames(file.Projects))
}

func projectMatchesWorkspace(profile cliProjectProfile, cwd string) bool {
	for _, root := range profile.WorkspaceRoots {
		if workspaceContainsPath(cwd, root) {
			return true
		}
	}
	return false
}

func workspaceContainsPath(cwd string, root string) bool {
	cleanCWD := normalizePath(cwd)
	cleanRoot := normalizePath(root)
	if cleanCWD == "" || cleanRoot == "" {
		return false
	}
	if cleanCWD == cleanRoot {
		return true
	}
	separator := string(filepath.Separator)
	return strings.HasPrefix(cleanCWD, cleanRoot+separator)
}

func normalizePath(value string) string {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return ""
	}
	cleaned := filepath.Clean(trimmed)
	if absolute, err := filepath.Abs(cleaned); err == nil {
		cleaned = absolute
	}
	if runtime.GOOS == "windows" {
		cleaned = strings.ToLower(cleaned)
	}
	return cleaned
}

func joinProjectNames(projects []cliProjectProfile) string {
	names := make([]string, 0, len(projects))
	for _, project := range projects {
		if name := strings.TrimSpace(project.Name); name != "" {
			names = append(names, name)
		}
	}
	return strings.Join(names, ", ")
}
