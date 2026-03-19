package cli

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type supabaseConfig struct {
	URL            string
	APIKey         string
	Schema         string
	LogsTable      string
	MetricsTable   string
	TimeoutSeconds int
}

func loadSupabaseConfig() (supabaseConfig, error) {
	cfg := supabaseConfig{
		URL:            firstNonEmptyEnv("APPLOGGER_SUPABASE_URL", "SUPABASE_URL"),
		APIKey:         firstNonEmptyEnv("APPLOGGER_SUPABASE_KEY", "SUPABASE_KEY"),
		Schema:         firstNonEmptyEnv("APPLOGGER_SUPABASE_SCHEMA"),
		LogsTable:      firstNonEmptyEnv("APPLOGGER_SUPABASE_LOG_TABLE"),
		MetricsTable:   firstNonEmptyEnv("APPLOGGER_SUPABASE_METRIC_TABLE"),
		TimeoutSeconds: 15,
	}

	if cfg.URL == "" {
		return cfg, fmt.Errorf("missing Supabase URL: set APPLOGGER_SUPABASE_URL or SUPABASE_URL")
	}
	if cfg.APIKey == "" {
		return cfg, fmt.Errorf("missing Supabase API key: set APPLOGGER_SUPABASE_KEY or SUPABASE_KEY (service_role key required for CLI reads)")
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
	if timeoutRaw := firstNonEmptyEnv("APPLOGGER_SUPABASE_TIMEOUT_SECONDS"); timeoutRaw != "" {
		timeoutSeconds, err := strconv.Atoi(timeoutRaw)
		if err != nil || timeoutSeconds < 1 || timeoutSeconds > 120 {
			return cfg, fmt.Errorf("invalid APPLOGGER_SUPABASE_TIMEOUT_SECONDS value %q (expected 1..120)", timeoutRaw)
		}
		cfg.TimeoutSeconds = timeoutSeconds
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
