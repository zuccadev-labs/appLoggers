package cli

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/spf13/cobra"
)

type sseHub struct {
	mu      sync.RWMutex
	clients map[chan string]struct{}
}

func newSSEHub() *sseHub {
	return &sseHub{clients: make(map[chan string]struct{})}
}

func (h *sseHub) subscribe() chan string {
	ch := make(chan string, 64)
	h.mu.Lock()
	h.clients[ch] = struct{}{}
	h.mu.Unlock()
	return ch
}

func (h *sseHub) unsubscribe(ch chan string) {
	h.mu.Lock()
	delete(h.clients, ch)
	h.mu.Unlock()
	close(ch)
}

func (h *sseHub) broadcast(msg string) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for ch := range h.clients {
		select {
		case ch <- msg:
		default:
			// drop if client buffer full
		}
	}
}

type serveStats struct {
	mu            sync.Mutex
	eventsServed  int
	clientCount   int
	lastPollAt    time.Time
	lastEventID   string
}

func newServeCommand() *cobra.Command {
	var port int
	var environment string
	var minSeverity string

	cmd := &cobra.Command{
		Use:   "serve",
		Short: "Start a local SSE server that streams live log events from Supabase",
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) > 0 {
				return newUsageError("serve does not accept positional arguments")
			}
			if port < 1024 || port > 65535 {
				return newUsageError("invalid --port value %d (expected 1024..65535)", port)
			}
			if minSeverity != "" {
				switch strings.ToLower(minSeverity) {
				case "debug", "info", "warn", "error", "critical":
				default:
					return newUsageError("invalid --min-severity value %q (expected debug|info|warn|error|critical)", minSeverity)
				}
			}

			cfg, err := loadSupabaseConfig()
			if err != nil {
				return err
			}

			hub := newSSEHub()
			stats := &serveStats{}

			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()

			go pollSupabase(ctx, cfg, hub, stats, environment, minSeverity)

			mux := http.NewServeMux()
			mux.HandleFunc("/events", func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Access-Control-Allow-Origin", "*")
				w.Header().Set("Content-Type", "text/event-stream")
				w.Header().Set("Cache-Control", "no-cache")
				w.Header().Set("Connection", "keep-alive")

				flusher, ok := w.(http.Flusher)
				if !ok {
					http.Error(w, "SSE not supported", http.StatusInternalServerError)
					return
				}

				ch := hub.subscribe()
				defer hub.unsubscribe(ch)

				stats.mu.Lock()
				stats.clientCount++
				stats.mu.Unlock()
				defer func() {
					stats.mu.Lock()
					stats.clientCount--
					stats.mu.Unlock()
				}()

				if _, err := fmt.Fprintf(w, "event: connected\ndata: {\"status\":\"connected\"}\n\n"); err != nil {
					return
				}
				flusher.Flush()

				for {
					select {
					case msg, ok := <-ch:
						if !ok {
							return
						}
						if _, err := fmt.Fprintf(w, "data: %s\n\n", msg); err != nil {
							return
						}
						flusher.Flush()
					case <-r.Context().Done():
						return
					}
				}
			})

			mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Access-Control-Allow-Origin", "*")
				w.Header().Set("Content-Type", "application/json")
				payload := map[string]any{
					"ok":      true,
					"status":  "running",
					"version": buildVersion,
				}
				_ = writeJSON(w, payload)
			})

			mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Access-Control-Allow-Origin", "*")
				w.Header().Set("Content-Type", "application/json")
				stats.mu.Lock()
				payload := map[string]any{
					"events_served": stats.eventsServed,
					"client_count":  stats.clientCount,
					"last_poll_at":  stats.lastPollAt.Format(time.RFC3339),
					"last_event_id": stats.lastEventID,
				}
				stats.mu.Unlock()
				_ = writeJSON(w, payload)
			})

			addr := fmt.Sprintf(":%d", port)
			_, printErr := fmt.Fprintf(cmd.OutOrStdout(), "AppLoggers serve listening on http://localhost%s\nPress Ctrl+C to stop.\n", addr)
			if printErr != nil {
				return printErr
			}

			server := &http.Server{
				Addr:    addr,
				Handler: mux,
			}
			return server.ListenAndServe()
		},
	}

	cmd.Flags().IntVar(&port, "port", 7070, "HTTP port to listen on (1024..65535)")
	cmd.Flags().StringVar(&environment, "environment", "", "Filter events by environment")
	cmd.Flags().StringVar(&minSeverity, "min-severity", "", "Minimum severity to stream (debug|info|warn|error|critical)")
	return cmd
}

func pollSupabase(ctx context.Context, cfg supabaseConfig, hub *sseHub, stats *serveStats, environment, minSeverity string) {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	var lastSeenID string

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			rows, err := fetchLatestEvents(ctx, cfg, environment, minSeverity, lastSeenID)
			if err != nil {
				continue
			}

			stats.mu.Lock()
			stats.lastPollAt = time.Now()
			stats.mu.Unlock()

			for i := len(rows) - 1; i >= 0; i-- {
				row := rows[i]
				id, _ := row["id"].(string)
				if id != "" {
					lastSeenID = id
				}
				data, err := json.Marshal(row)
				if err != nil {
					continue
				}
				hub.broadcast(string(data))
				stats.mu.Lock()
				stats.eventsServed++
				stats.lastEventID = id
				stats.mu.Unlock()
			}
		}
	}
}

func fetchLatestEvents(ctx context.Context, cfg supabaseConfig, environment, minSeverity, afterID string) ([]map[string]any, error) {
	req := telemetryQueryRequest{
		Source:      "logs",
		Environment: environment,
		MinSeverity: minSeverity,
		Limit:       20,
		Order:       "desc",
	}
	return doQueryWithRetry(ctx, cfg, req, 1)
}

