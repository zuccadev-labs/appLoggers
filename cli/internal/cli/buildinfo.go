package cli

var (
	buildVersion = "dev"
	buildCommit  = "none"
	buildDate    = "unknown"
)

func SetBuildInfo(version, commit, date string) {
	if version != "" {
		buildVersion = version
	}
	if commit != "" {
		buildCommit = commit
	}
	if date != "" {
		buildDate = date
	}
}
