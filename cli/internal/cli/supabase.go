package cli

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type supabaseConfig struct {
	Project         string
	ConfigSource    string
	URL             string
	APIKey          string
	Schema          string
	LogsTable       string
	MetricsTable    string
	TimeoutSeconds  int
	IntegritySecret string
}

func loadSupabaseConfig() (supabaseConfig, error) {
	if cfg, err := loadSupabaseConfigFromProjectConfig(); err == nil {
		return cfg, nil
	} else if !os.IsNotExist(err) {
		return supabaseConfig{}, err
	}

	// Try local.properties before falling back to env vars
	if cfg, err := loadSupabaseConfigFromLocalProperties(); err == nil {
		return cfg, nil
	}

	cfg := supabaseConfig{
		ConfigSource:    "environment",
		URL:             firstNonEmptyEnv("appLogger_supabaseUrl", "APPLOGGER_SUPABASE_URL", "SUPABASE_URL"),
		APIKey:          firstNonEmptyEnv("appLogger_supabaseKey", "APPLOGGER_SUPABASE_KEY", "SUPABASE_KEY"),
		Schema:          firstNonEmptyEnv("appLogger_supabaseSchema", "APPLOGGER_SUPABASE_SCHEMA"),
		LogsTable:       firstNonEmptyEnv("appLogger_supabaseLogTable", "APPLOGGER_SUPABASE_LOG_TABLE"),
		MetricsTable:    firstNonEmptyEnv("appLogger_supabaseMetricTable", "APPLOGGER_SUPABASE_METRIC_TABLE"),
		IntegritySecret: firstNonEmptyEnv("APPLOGGER_INTEGRITY_SECRET", "appLogger_integritySecret"),
		TimeoutSeconds:  15,
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
	if timeoutRaw := firstNonEmptyEnv("appLogger_supabaseTimeoutSeconds", "APPLOGGER_SUPABASE_TIMEOUT_SECONDS"); timeoutRaw != "" {
		timeoutSeconds, err := strconv.Atoi(timeoutRaw)
		if err != nil || timeoutSeconds < 1 || timeoutSeconds > 120 {
			return cfg, fmt.Errorf("invalid appLogger_supabaseTimeoutSeconds value %q (expected 1..120)", timeoutRaw)
		}
		cfg.TimeoutSeconds = timeoutSeconds
	}

	return validateSupabaseConfig(cfg)
}

func validateSupabaseConfig(cfg supabaseConfig) (supabaseConfig, error) {
	if cfg.URL == "" {
		return cfg, fmt.Errorf("missing Supabase URL: set appLogger_supabaseUrl, APPLOGGER_SUPABASE_URL, or SUPABASE_URL, or configure a project profile via APPLOGGER_CONFIG")
	}
	if cfg.APIKey == "" {
		return cfg, fmt.Errorf("missing Supabase API key: set appLogger_supabaseKey, APPLOGGER_SUPABASE_KEY, or SUPABASE_KEY (service_role key required for CLI reads), or configure a project profile via APPLOGGER_CONFIG")
	}
	if cfg.TimeoutSeconds < 1 || cfg.TimeoutSeconds > 120 {
		return cfg, fmt.Errorf("invalid appLogger_supabaseTimeoutSeconds value %d (expected 1..120)", cfg.TimeoutSeconds)
	}
	if !strings.HasPrefix(strings.ToLower(cfg.URL), "http://") && !strings.HasPrefix(strings.ToLower(cfg.URL), "https://") {
		return cfg, fmt.Errorf("invalid Supabase URL %q (expected http/https)", cfg.URL)
	}

	return cfg, nil
}

func firstNonEmptyEnv(keys ...string) string {
	for _, key := range keys {
		value := strings.TrimSpace(os.Getenv(key))
		if value != "" {
			return value
		}
	}
	return ""
}

func (c supabaseConfig) timeout() time.Duration {
	return time.Duration(c.TimeoutSeconds) * time.Second
}

// loadSupabaseConfigFromLocalProperties reads Supabase credentials from local.properties
// in the current working directory or its workspace roots.
func loadSupabaseConfigFromLocalProperties() (supabaseConfig, error) {
	cwd, err := os.Getwd()
	if err != nil {
		return supabaseConfig{}, os.ErrNotExist
	}

	lpPath := cwd + string(os.PathSeparator) + "local.properties"
	content, err := os.ReadFile(lpPath)
	if err != nil {
		return supabaseConfig{}, os.ErrNotExist
	}

	props := parsePropertiesFile(string(content))

	supabaseURL := firstNonEmpty(
		props["appLogger.supabaseUrl"],
		props["SUPABASE_URL"],
	)
	apiKey := firstNonEmpty(
		props["appLogger.supabaseKey"],
		props["SUPABASE_KEY"],
	)

	if supabaseURL == "" || apiKey == "" {
		return supabaseConfig{}, os.ErrNotExist
	}

	cfg := supabaseConfig{
		ConfigSource:   "local_properties",
		URL:            supabaseURL,
		APIKey:         apiKey,
		Schema:         firstNonEmpty(props["appLogger.supabaseSchema"], "public"),
		LogsTable:      firstNonEmpty(props["appLogger.supabaseLogTable"], "app_logs"),
		MetricsTable:   firstNonEmpty(props["appLogger.supabaseMetricTable"], "app_metrics"),
		TimeoutSeconds: 15,
	}
	return validateSupabaseConfig(cfg)
}

// parsePropertiesFile parses a Java .properties-style file (key=value lines, # comments).
func parsePropertiesFile(content string) map[string]string {
	result := make(map[string]string)
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx < 0 {
			continue
		}
		key := strings.TrimSpace(line[:idx])
		value := strings.TrimSpace(line[idx+1:])
		if key != "" {
			result[key] = value
		}
	}
	return result
}

// firstNonEmpty returns the first non-empty string from the provided values.
func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}
