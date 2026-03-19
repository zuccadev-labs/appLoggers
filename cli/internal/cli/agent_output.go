package cli

import (
	"fmt"
	"io"

	toon "github.com/toon-format/toon-go"
)

func writeAgent(out io.Writer, payload any) error {
	doc, err := toon.MarshalString(payload, toon.WithLengthMarkers(true))
	if err != nil {
		return fmt.Errorf("failed to encode agent output: %w", err)
	}
	_, err = fmt.Fprintln(out, doc)
	return err
}
