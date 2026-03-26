package cli

import "encoding/json"

// decodeJSONBytes decodes JSON from a byte slice into v.
func decodeJSONBytes(data []byte, v any) error {
	return json.Unmarshal(data, v)
}
