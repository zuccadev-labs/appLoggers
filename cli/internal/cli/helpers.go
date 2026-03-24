package cli

import (
	"encoding/json"
	"io"
)

// decodeJSON decodes JSON from r into v.
func decodeJSON(r io.Reader, v any) error {
	return json.NewDecoder(r).Decode(v)
}

// decodeJSONBytes decodes JSON from a byte slice into v.
func decodeJSONBytes(data []byte, v any) error {
	return json.Unmarshal(data, v)
}
