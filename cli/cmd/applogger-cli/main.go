package main

import (
	"os"

	"github.com/devzucca/appLoggers/cli/internal/cli"
)

var (
	version = "dev"
	commit  = "none"
	date    = "unknown"
)

func main() {
	cli.SetBuildInfo(version, commit, date)
	os.Exit(cli.Execute())
}
