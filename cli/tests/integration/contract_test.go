package integration_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
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
	if !strings.Contains(text, "\"name\": \"applogger-cli\"") {
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
	if payload["name"] != "applogger-cli" {
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
	if !strings.Contains(text, "name: applogger-cli") {
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
		"APPLOGGER_SUPABASE_URL="+mockSupabase.URL,
		"APPLOGGER_SUPABASE_KEY=test-key",
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
		"APPLOGGER_SUPABASE_URL="+mockSupabase.URL,
		"APPLOGGER_SUPABASE_KEY=test-key",
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
		"APPLOGGER_SUPABASE_URL="+mockSupabase.URL,
		"APPLOGGER_SUPABASE_KEY=test-key",
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
