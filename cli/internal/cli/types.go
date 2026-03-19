package cli

import "fmt"

const (
	exitCodeSuccess = 0
	exitCodeError   = 1
	exitCodeUsage   = 2
)

type usageError struct {
	msg string
}

func (e usageError) Error() string {
	return e.msg
}

func newUsageError(format string, args ...any) error {
	return usageError{msg: fmt.Sprintf(format, args...)}
}
