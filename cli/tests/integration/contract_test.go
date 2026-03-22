package integration_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestSyncbinMetadataJSON(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "--syncbin-metadata", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("metadata command failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"name\": \"apploggers\"") {
		t.Fatalf("expected metadata name in output, got: %s", text)
	}
}

func TestUsageExitCode(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "version", "extra")
	err := cmd.Run()
	if err == nil {
		t.Fatal("expected usage error")
	}
	exitCode := cmd.ProcessState.ExitCode()
	if exitCode != 2 {
		t.Fatalf("expected exit code 2, got %d", exitCode)
	}
}

func TestCapabilitiesJSON(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "capabilities", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("capabilities command failed: %v, output=%s", err, string(out))
	}
	var payload map[string]any
	if err := json.Unmarshal(out, &payload); err != nil {
		t.Fatalf("invalid json output: %v, output=%s", err, string(out))
	}
	if payload["name"] != "apploggers" {
		t.Fatalf("unexpected name: %#v", payload["name"])
	}
}

func TestAgentSchemaJSON(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "agent", "schema", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("agent schema command failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"contract_version\": \"1.0.0\"") {
		t.Fatalf("expected contract version, output=%s", text)
	}
}

func TestHealthJSON(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "health", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("health command failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"status\": \"ready\"") {
		t.Fatalf("expected ready status, output=%s", text)
	}
}

func TestUsageErrorJSONEnvelope(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "version", "extra", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	text := string(out)
	if !strings.Contains(text, "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage_error envelope, output=%s", text)
	}
}

func TestVersionAgentOutput(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "version", "--output", "agent")
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("version agent output failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "name: apploggers") {
		t.Fatalf("expected toon field name, output=%s", text)
	}
	if !strings.Contains(text, "version:") {
		t.Fatalf("expected version field in toon output, output=%s", text)
	}
}

func TestUsageErrorAgentEnvelope(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "version", "extra", "--output", "agent")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	text := string(out)
	if !strings.Contains(text, "error_kind: usage_error") {
		t.Fatalf("expected usage error kind in agent envelope, output=%s", text)
	}
}

func TestTelemetryQueryContractJSON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/v1/app_logs" {
			http.Error(w, "unexpected path", http.StatusNotFound)
			return
		}
		if r.URL.Query().Get("limit") != "25" {
			http.Error(w, "unexpected limit", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("level") != "eq.ERROR" {
			http.Error(w, "unexpected level filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("created_at") == "" {
			http.Error(w, "expected created_at range filters", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"ERROR","tag":"api","message":"boom"},
			{"id":"2","created_at":"2026-03-01T00:10:00Z","level":"ERROR","tag":"db","message":"bad"}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"query",
		"--source", "logs",
		"--from", "2026-03-01T00:00:00Z",
		"--to", "2026-03-02T00:00:00Z",
		"--severity", "error",
		"--aggregate", "hour",
		"--limit", "25",
		"--output", "json",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry query failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"ok\": true") {
		t.Fatalf("expected successful envelope, output=%s", text)
	}
	if !strings.Contains(text, "\"count\": 2") {
		t.Fatalf("expected row count, output=%s", text)
	}
	if !strings.Contains(text, "\"severity\": \"error\"") {
		t.Fatalf("expected severity echo in response, output=%s", text)
	}
	if !strings.Contains(text, "\"summary\":") {
		t.Fatalf("expected summary aggregation block, output=%s", text)
	}
	if !strings.Contains(text, "\"by\": \"hour\"") {
		t.Fatalf("expected hour aggregation, output=%s", text)
	}
}

func TestTelemetryQueryMissingEnvJSON(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected runtime error when env vars are missing")
	}
	if cmd.ProcessState.ExitCode() != 1 {
		t.Fatalf("expected exit code 1, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"runtime_error\"") {
		t.Fatalf("expected runtime_error envelope, output=%s", string(out))
	}
}

func TestTelemetryAgentResponseTOON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"ERROR","tag":"api","message":"boom"},
			{"id":"2","created_at":"2026-03-01T00:10:00Z","level":"WARN","tag":"db","message":"warn"}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"agent-response",
		"--source", "logs",
		"--aggregate", "severity",
		"--preview-limit", "1",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("agent-response failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "kind: telemetry_agent_response") {
		t.Fatalf("expected agent response kind, output=%s", text)
	}
	if !strings.Contains(text, "source: logs") {
		t.Fatalf("expected source in TOON output, output=%s", text)
	}
	if !strings.Contains(text, "rows_preview") {
		t.Fatalf("expected preview rows in TOON output, output=%s", text)
	}
}

func TestTelemetryQueryInvalidAggregateForSource(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--source", "metrics", "--aggregate", "severity", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error for invalid aggregate/source combination")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage error envelope, output=%s", string(out))
	}
}

func TestTelemetryQueryMetricsNameFilterJSON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/v1/app_metrics" {
			http.Error(w, "unexpected path", http.StatusNotFound)
			return
		}
		if r.URL.Query().Get("name") != "eq.response_time_ms" {
			http.Error(w, "unexpected name filter", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","name":"response_time_ms","value":123.4,"unit":"ms"}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"query",
		"--source", "metrics",
		"--name", "response_time_ms",
		"--aggregate", "name",
		"--limit", "10",
		"--output", "json",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry metrics name query failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"source\": \"metrics\"") {
		t.Fatalf("expected metrics source, output=%s", text)
	}
	if !strings.Contains(text, "\"name\": \"response_time_ms\"") {
		t.Fatalf("expected name filter echo in response, output=%s", text)
	}
}

func TestTelemetryQueryLogsAnomalyTypeFilterAndExtraJSON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/v1/app_logs" {
			http.Error(w, "unexpected path", http.StatusNotFound)
			return
		}
		if r.URL.Query().Get("level") != "eq.WARN" {
			http.Error(w, "unexpected level filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("extra->>anomaly_type") != "eq.slow_response" {
			http.Error(w, "unexpected anomaly_type filter", http.StatusBadRequest)
			return
		}
		if !strings.Contains(r.URL.RawQuery, "select=id%2Ccreated_at%2Clevel%2Ctag%2Cmessage%2Csession_id%2Cdevice_id%2Cuser_id%2Csdk_version%2Cextra") {
			http.Error(w, "expected extra in select columns", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"WARN","tag":"network","message":"slow call","session_id":"s-1","sdk_version":"dev","extra":{"anomaly_type":"slow_response","latency_ms":"2500"}}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"query",
		"--source", "logs",
		"--severity", "warn",
		"--anomaly-type", "slow_response",
		"--limit", "10",
		"--output", "json",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry anomaly_type query failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"anomaly_type\": \"slow_response\"") {
		t.Fatalf("expected anomaly_type echo in response, output=%s", text)
	}
	if !strings.Contains(text, "\"extra\": {") {
		t.Fatalf("expected extra object in response rows, output=%s", text)
	}
	if !strings.Contains(text, "\"latency_ms\": \"2500\"") {
		t.Fatalf("expected extra payload content in response rows, output=%s", text)
	}
	if !strings.Contains(text, "\"severity\": \"warn\"") {
		t.Fatalf("expected severity echo in request block, output=%s", text)
	}
}

func TestTelemetryQueryAnomalyTypeInvalidForMetrics(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--source", "metrics", "--anomaly-type", "slow_response", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error for --anomaly-type with metrics source")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage error envelope, output=%s", string(out))
	}
}

func TestTelemetryQueryIdentityFiltersJSON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/v1/app_logs" {
			http.Error(w, "unexpected path", http.StatusNotFound)
			return
		}
		if r.URL.Query().Get("session_id") != "eq.session-mobile-01" {
			http.Error(w, "unexpected session_id filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("device_id") != "eq.device-abc" {
			http.Error(w, "unexpected device_id filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("user_id") != "eq.user-anon-001" {
			http.Error(w, "unexpected user_id filter", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"INFO","tag":"player","message":"ok","session_id":"session-mobile-01","device_id":"device-abc","user_id":"user-anon-001"}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"query",
		"--source", "logs",
		"--session-id", "session-mobile-01",
		"--device-id", "device-abc",
		"--user-id", "user-anon-001",
		"--limit", "10",
		"--output", "json",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry identity filter query failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"session_id\": \"session-mobile-01\"") {
		t.Fatalf("expected session_id echo in request block, output=%s", text)
	}
	if !strings.Contains(text, "\"device_id\": \"device-abc\"") {
		t.Fatalf("expected device_id echo in request block, output=%s", text)
	}
	if !strings.Contains(text, "\"user_id\": \"user-anon-001\"") {
		t.Fatalf("expected user_id echo in request block, output=%s", text)
	}
}

func TestTelemetryQueryUserIDInvalidForMetrics(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--source", "metrics", "--user-id", "anon-001", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error for --user-id with metrics source")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage error envelope, output=%s", string(out))
	}
}

func TestTelemetryQueryAdvancedLogFiltersJSON(t *testing.T) {
	binary := buildCLI(t)
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/v1/app_logs" {
			http.Error(w, "unexpected path", http.StatusNotFound)
			return
		}
		if r.URL.Query().Get("extra->>package_name") != "eq.com.company.billing" {
			http.Error(w, "unexpected package filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("extra->>error_code") != "eq.E-42" {
			http.Error(w, "unexpected error_code filter", http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("message") != "ilike.*timeout*" {
			http.Error(w, "unexpected contains filter", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"ERROR","tag":"billing","message":"timeout on payment","extra":{"package_name":"com.company.billing","error_code":"E-42"}}
		]`))
	}))
	defer mockSupabase.Close()

	cmd := exec.Command(
		binary,
		"telemetry",
		"query",
		"--source", "logs",
		"--package", "com.company.billing",
		"--error-code", "E-42",
		"--contains", "timeout",
		"--limit", "10",
		"--output", "json",
	)
	cmd.Env = append(cmd.Env,
		"appLogger_supabaseUrl="+mockSupabase.URL,
		"appLogger_supabaseKey=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry advanced filter query failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"package\": \"com.company.billing\"") {
		t.Fatalf("expected package echo in request block, output=%s", text)
	}
	if !strings.Contains(text, "\"error_code\": \"E-42\"") {
		t.Fatalf("expected error_code echo in request block, output=%s", text)
	}
	if !strings.Contains(text, "\"contains\": \"timeout\"") {
		t.Fatalf("expected contains echo in request block, output=%s", text)
	}
}

func TestTelemetryQueryPackageInvalidForMetrics(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--source", "metrics", "--package", "pkg.demo", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error for --package with metrics source")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage error envelope, output=%s", string(out))
	}
}

func TestTelemetryQueryProjectProfileExplicitJSON(t *testing.T) {
	binary := buildCLI(t)
	workspace := t.TempDir()
	configPath := filepath.Join(t.TempDir(), "applogger-cli.json")
	writeProjectConfig(t, configPath, map[string]any{
		"projects": []map[string]any{
			{
				"name":            "klinema",
				"workspace_roots": []string{workspace},
				"supabase": map[string]any{
					"url":         "http://placeholder.invalid",
					"api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
				},
			},
		},
	})

	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[ {"id":"1","created_at":"2026-03-01T00:00:00Z","level":"ERROR","tag":"api","message":"boom"} ]`))
	}))
	defer mockSupabase.Close()

	writeProjectConfig(t, configPath, map[string]any{
		"projects": []map[string]any{
			{
				"name":            "klinema",
				"workspace_roots": []string{workspace},
				"supabase": map[string]any{
					"url":         mockSupabase.URL,
					"api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
				},
			},
		},
	})

	cmd := exec.Command(binary, "--project", "klinema", "telemetry", "query", "--source", "logs", "--limit", "10", "--output", "json")
	cmd.Dir = workspace
	cmd.Env = append(os.Environ(),
		"APPLOGGER_CONFIG="+configPath,
		"APPLOGGER_KLINEMA_SUPABASE_KEY=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry query with explicit project failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"project\": \"klinema\"") {
		t.Fatalf("expected project name in response, output=%s", text)
	}
	if !strings.Contains(text, "\"config_source\": \"project_config\"") {
		t.Fatalf("expected project config source in response, output=%s", text)
	}
}

func TestTelemetryQueryProjectProfileSingleProjectAutoSelectionJSON(t *testing.T) {
	binary := buildCLI(t)
	configPath := filepath.Join(t.TempDir(), "applogger-cli.json")
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[ {"id":"1","created_at":"2026-03-01T00:00:00Z","name":"response_time_ms","value":123.4,"unit":"ms"} ]`))
	}))
	defer mockSupabase.Close()

	writeProjectConfig(t, configPath, map[string]any{
		"projects": []map[string]any{
			{
				"name": "klinematv",
				"supabase": map[string]any{
					"url":         mockSupabase.URL,
					"api_key_env": "APPLOGGER_KLINEMATV_SUPABASE_KEY",
				},
			},
		},
	})

	cmd := exec.Command(binary, "telemetry", "query", "--source", "metrics", "--name", "response_time_ms", "--limit", "10", "--output", "json")
	cmd.Env = append(os.Environ(),
		"APPLOGGER_CONFIG="+configPath,
		"APPLOGGER_KLINEMATV_SUPABASE_KEY=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("telemetry query with single-project auto selection failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "\"project\": \"klinematv\"") {
		t.Fatalf("expected single configured project name in response, output=%s", text)
	}
	if !strings.Contains(text, "\"source\": \"metrics\"") {
		t.Fatalf("expected metrics source in response, output=%s", text)
	}
}

func TestTelemetryAgentResponseProjectProfileTOON(t *testing.T) {
	binary := buildCLI(t)
	configPath := filepath.Join(t.TempDir(), "applogger-cli.json")
	mockSupabase := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[
			{"id":"1","created_at":"2026-03-01T00:00:00Z","level":"ERROR","tag":"api","message":"boom"}
		]`))
	}))
	defer mockSupabase.Close()

	writeProjectConfig(t, configPath, map[string]any{
		"projects": []map[string]any{
			{
				"name": "klinema",
				"supabase": map[string]any{
					"url":         mockSupabase.URL,
					"api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
				},
			},
		},
	})

	cmd := exec.Command(binary, "--project", "klinema", "telemetry", "agent-response", "--source", "logs", "--preview-limit", "1")
	cmd.Env = append(os.Environ(),
		"APPLOGGER_CONFIG="+configPath,
		"APPLOGGER_KLINEMA_SUPABASE_KEY=test-key",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("agent-response with project profile failed: %v, output=%s", err, string(out))
	}
	text := string(out)
	if !strings.Contains(text, "project: klinema") {
		t.Fatalf("expected project in TOON output, output=%s", text)
	}
	if !strings.Contains(text, "config_source: project_config") {
		t.Fatalf("expected config_source in TOON output, output=%s", text)
	}
}

func TestTelemetryQueryProjectProfileAmbiguousWorkspaceFails(t *testing.T) {
	binary := buildCLI(t)
	workspace := t.TempDir()
	configPath := filepath.Join(t.TempDir(), "applogger-cli.json")
	writeProjectConfig(t, configPath, map[string]any{
		"projects": []map[string]any{
			{
				"name":            "klinema",
				"workspace_roots": []string{workspace},
				"supabase": map[string]any{
					"url":         "https://klinema.supabase.co",
					"api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
				},
			},
			{
				"name":            "klinematv",
				"workspace_roots": []string{workspace},
				"supabase": map[string]any{
					"url":         "https://klinematv.supabase.co",
					"api_key_env": "APPLOGGER_KLINEMATV_SUPABASE_KEY",
				},
			},
		},
	})

	cmd := exec.Command(binary, "telemetry", "query", "--output", "json")
	cmd.Dir = workspace
	cmd.Env = append(os.Environ(), "APPLOGGER_CONFIG="+configPath)
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected ambiguous workspace resolution to fail")
	}
	if !strings.Contains(string(out), "multiple projects") || !strings.Contains(string(out), "use --project or APPLOGGER_PROJECT") {
		t.Fatalf("expected ambiguity error, output=%s", string(out))
	}
}

func writeProjectConfig(t *testing.T, path string, payload any) {
	t.Helper()
	content, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("failed to marshal project config: %v", err)
	}
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatalf("failed to write project config: %v", err)
	}
}

func TestTelemetryQueryNameFilterInvalidForLogs(t *testing.T) {
	binary := buildCLI(t)
	cmd := exec.Command(binary, "telemetry", "query", "--source", "logs", "--name", "response_time_ms", "--output", "json")
	out, err := cmd.CombinedOutput()
	if err == nil {
		t.Fatal("expected usage error for --name with logs source")
	}
	if cmd.ProcessState.ExitCode() != 2 {
		t.Fatalf("expected exit code 2, got %d", cmd.ProcessState.ExitCode())
	}
	if !strings.Contains(string(out), "\"error_kind\": \"usage_error\"") {
		t.Fatalf("expected usage error envelope, output=%s", string(out))
	}
}

func buildCLI(t *testing.T) string {
	t.Helper()
	root := projectRoot(t)
	binary := filepath.Join(t.TempDir(), "applogger-cli")
	if runtime.GOOS == "windows" {
		binary += ".exe"
	}
	cmd := exec.Command("go", "build", "-o", binary, "./cmd/applogger-cli")
	cmd.Dir = root
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("go build failed: %v, output=%s", err, string(out))
	}
	return binary
}

func projectRoot(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("unable to resolve runtime caller")
	}
	return filepath.Clean(filepath.Join(filepath.Dir(file), "..", ".."))
}
