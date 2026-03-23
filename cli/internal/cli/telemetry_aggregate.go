package cli

import (
	"fmt"
	"sort"
	"strings"
	"time"
)

type aggregationBucket struct {
	Key   string `json:"key" toon:"key"`
	Count int    `json:"count" toon:"count"`
}

type telemetryAggregation struct {
	By      string              `json:"by" toon:"by"`
	Buckets []aggregationBucket `json:"buckets" toon:"buckets"`
}

// validAggregateModes lists all supported aggregation modes.
var validAggregateModes = []string{"none", "hour", "day", "week", "severity", "tag", "session", "name", "environment"}

func buildAggregation(by string, rows []map[string]any) (*telemetryAggregation, error) {
	normalized := strings.ToLower(strings.TrimSpace(by))
	if normalized == "" || normalized == "none" {
		return nil, nil
	}

	counts := map[string]int{}
	for _, row := range rows {
		key, err := aggregateKey(normalized, row)
		if err != nil {
			return nil, err
		}
		counts[key]++
	}

	buckets := make([]aggregationBucket, 0, len(counts))
	for key, count := range counts {
		buckets = append(buckets, aggregationBucket{Key: key, Count: count})
	}
	sort.SliceStable(buckets, func(i, j int) bool {
		if buckets[i].Count == buckets[j].Count {
			return buckets[i].Key < buckets[j].Key
		}
		return buckets[i].Count > buckets[j].Count
	})

	return &telemetryAggregation{By: normalized, Buckets: buckets}, nil
}

func aggregateKey(by string, row map[string]any) (string, error) {
	switch by {
	case "hour":
		return truncateTimestamp(row, time.Hour, "2006-01-02T15:00Z")
	case "day":
		return truncateTimestamp(row, 24*time.Hour, "2006-01-02")
	case "week":
		createdAt := strings.TrimSpace(fmt.Sprint(row["created_at"]))
		if createdAt == "" {
			return "unknown", nil
		}
		ts, err := time.Parse(time.RFC3339, createdAt)
		if err != nil {
			return "unknown", nil
		}
		// Truncate to Monday of the week
		weekday := int(ts.UTC().Weekday())
		if weekday == 0 {
			weekday = 7 // Sunday → 7 so Monday is always day 1
		}
		monday := ts.UTC().AddDate(0, 0, -(weekday - 1)).Truncate(24 * time.Hour)
		return monday.Format("2006-01-02"), nil
	case "severity":
		value := strings.ToUpper(strings.TrimSpace(fmt.Sprint(row["level"])))
		if value == "" {
			return "UNKNOWN", nil
		}
		return value, nil
	case "tag":
		value := strings.TrimSpace(fmt.Sprint(row["tag"]))
		if value == "" {
			return "(empty)", nil
		}
		return value, nil
	case "session":
		value := strings.TrimSpace(fmt.Sprint(row["session_id"]))
		if value == "" || value == "<nil>" {
			return "(none)", nil
		}
		return value, nil
	case "name":
		value := strings.TrimSpace(fmt.Sprint(row["name"]))
		if value == "" {
			return "(empty)", nil
		}
		return value, nil
	case "environment":
		value := strings.TrimSpace(fmt.Sprint(row["environment"]))
		if value == "" || value == "<nil>" {
			return "(none)", nil
		}
		return value, nil
	default:
		return "", fmt.Errorf("unsupported aggregate mode %q", by)
	}
}

func truncateTimestamp(row map[string]any, d time.Duration, layout string) (string, error) {
	createdAt := strings.TrimSpace(fmt.Sprint(row["created_at"]))
	if createdAt == "" {
		return "unknown", nil
	}
	ts, err := time.Parse(time.RFC3339, createdAt)
	if err != nil {
		return "unknown", nil
	}
	return ts.UTC().Truncate(d).Format(layout), nil
}
