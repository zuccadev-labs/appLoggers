# Syncbin Child CLI Architect - AI Agent Skill

## Metadata
- **Skill ID**: `syncbin-child-cli-architect`
- **Version**: `2.0.0`
- **Category**: Full-Stack CLI Development, Enterprise Architecture, Syncbin Standard
- **Complexity**: Expert
- **Prerequisites**: Programming experience (Go/Python/Bash), CLI design, Syncbin standard knowledge

## Purpose
This skill enables AI agents to architect and build COMPLETE, production-ready Child CLIs for the Syncbin ecosystem from scratch. The agent will master the full lifecycle: requirements analysis, architecture design, implementation following Syncbin standards, testing, documentation, OCI packaging, and marketplace publishing. This is the comprehensive skill for creating professional-grade CLI tools.

## AppLoggers Delivery Cadence

When this skill is used inside the AppLoggers repository:

1. Do not push every incremental step while building the CLI.
2. Push only after a complete CLI milestone is closed (for example: foundation complete, telemetry query module complete, installer module complete).
3. If the milestone is docs-only, do not run manual build/test commands before push.

## Core Concepts

### 1. The Syncbin Child CLI Standard
A complete Child CLI must implement ALL of these components:

**A. Universal Contract (Non-Negotiable)**
```bash
# Metadata discovery
cli-name --syncbin-metadata
# Output: {"name":"cli-name","version":"v1.0.0","description":"..."}

# Structured output
cli-name command --output json
cli-name command --output text

# POSIX exit codes
0 = Success, 1 = Error, 2 = Usage error
```

**B. Project Structure (Professional Layout)**
```
my-cli/
├── cmd/
│   └── my-cli/
│       └── main.go              # Entry point
├── internal/
│   ├── cli/                     # CLI commands
│   │   ├── root.go
│   │   └── subcommands.go
│   ├── core/                    # Business logic
│   │   └── service.go
│   └── config/                  # Configuration
│       └── config.go
├── pkg/                         # Public libraries (optional)
├── tests/
│   ├── unit/
│   └── integration/
├── docs/
│   ├── README.md
│   └── USAGE.md
├── .github/workflows/           # CI/CD
│   └── publish.yml
├── plugin-metadata.yaml         # Syncbin contract
├── go.mod
├── Makefile
└── README.md
```

**C. Quality Standards**
- Test coverage: 60%+ minimum
- Linting: golangci-lint passing
- Documentation: Complete README + usage examples
- Error handling: All errors wrapped with context
- Logging: Structured logging (log/slog)
- Configuration: Support config files + env vars + flags

**D. OCI Distribution**
- Multi-platform binaries (linux-amd64, linux-arm64, darwin-amd64, darwin-arm64)
- Automated CI/CD pipeline
- Semantic versioning
- Published to Syncbin marketplace

### 2. Architecture Decision Framework

**Step 1: Define CLI Purpose**
- What problem does it solve?
- Who is the target user?
- What are the core operations?
- What external dependencies exist?

**Step 2: Choose Resolution Strategy**
```
Does the CLI need external tools?
├─ NO → EMBEDDED
│   - Self-contained binary
│   - All logic in Go/Rust
│   - Example: Database backup tool
│
├─ YES → Is the tool >50MB?
│   ├─ YES → HOOKS
│   │   - Download on install
│   │   - Example: Terraform wrapper
│   │
│   └─ NO → Is it a system tool?
│       ├─ YES → WRAPPER
│       │   - Delegate to OS binary
│       │   - Example: SSH with profiles
│       │
│       └─ NO → EMBEDDED
│           - Bundle dependencies
```

**Step 3: Design Command Structure**
```
my-cli
├── config                       # Configuration management
│   ├── init
│   ├── show
│   └── validate
├── resource                     # Main operations
│   ├── create
│   ├── list
│   ├── get
│   ├── update
│   └── delete
└── version                      # Version info
```

### 3. Implementation Phases

**Phase 1: Foundation (Day 1)**
- Project structure setup
- Universal Contract implementation
- Basic CLI framework (Cobra/urfave/cli)
- Configuration loading
- Logging setup

**Phase 2: Core Logic (Day 2-3)**
- Business logic implementation
- Error handling
- Input validation
- Output formatting (text/json)

**Phase 3: Quality (Day 4)**
- Unit tests (60%+ coverage)
- Integration tests
- Linting fixes
- Documentation

**Phase 4: Distribution (Day 5)**
- Multi-platform builds
- plugin-metadata.yaml
- CI/CD pipeline
- OCI packaging

## Agent Capabilities

### Capability 1: Requirements Analysis & Architecture Design
**When to use**: Starting a new CLI project from scratch

**Process**:
1. **Gather Requirements**
```markdown
# CLI Requirements Document

## Purpose
What problem does this CLI solve?

## Target Users
- DevOps engineers
- Developers
- System administrators

## Core Features
1. Feature A - Description
2. Feature B - Description
3. Feature C - Description

## External Dependencies
- Tool X (version Y)
- API Z

## Performance Requirements
- Startup time: <100ms
- Memory usage: <50MB
- Concurrent operations: Yes/No
```

2. **Design Command Structure**
```yaml
# commands.yaml
cli-name:
  description: "Main CLI tool"
  commands:
    - name: init
      description: "Initialize configuration"
      flags:
        - name: config-path
          type: string
          default: "~/.cli-name/config.yaml"
    
    - name: resource
      description: "Manage resources"
      subcommands:
        - name: create
          args: ["name"]
          flags:
            - name: type
              type: string
              required: true
        - name: list
          flags:
            - name: filter
              type: string
```

3. **Choose Technology Stack**
```
Language: Go (recommended for performance)
CLI Framework: Cobra (most popular)
Config: Viper (supports YAML/JSON/ENV)
Logging: log/slog (standard library)
Testing: testify (assertions)
```

### Capability 2: Implement Complete CLI in Go
**When to use**: Building production-ready CLI with full features

**Complete Implementation Template**:

**Step 1: Project Setup**
```bash
# Create project structure
mkdir -p my-cli/{cmd/my-cli,internal/{cli,core,config},tests/{unit,integration},docs}
cd my-cli

# Initialize Go module
go mod init github.com/yourorg/my-cli

# Install dependencies
go get github.com/spf13/cobra@latest
go get github.com/spf13/viper@latest
go get github.com/stretchr/testify@latest
```

**Step 2: Main Entry Point** (`cmd/my-cli/main.go`)
```go
package main

import (
    "fmt"
    "os"
    
    "github.com/yourorg/my-cli/internal/cli"
)

var (
    Version = "dev"
    Commit  = "none"
    Date    = "unknown"
)

func main() {
    // Set version info
    cli.SetVersion(Version, Commit, Date)
    
    // Execute CLI
    if err := cli.Execute(); err != nil {
        fmt.Fprintf(os.Stderr, "Error: %v\n", err)
        os.Exit(1)
    }
}
```

**Step 3: Root Command** (`internal/cli/root.go`)
```go
package cli

import (
    "encoding/json"
    "fmt"
    "log/slog"
    "os"
    
    "github.com/spf13/cobra"
    "github.com/spf13/viper"
)

var (
    cfgFile      string
    outputFormat string
    verbose      bool
    logger       *slog.Logger
)

var rootCmd = &cobra.Command{
    Use:   "my-cli",
    Short: "Professional CLI tool following Syncbin standard",
    Long:  `A complete, production-ready CLI tool that implements the Syncbin universal contract.`,
    PersistentPreRun: func(cmd *cobra.Command, args []string) {
        setupLogger()
    },
}

func Execute() error {
    return rootCmd.Execute()
}

func init() {
    cobra.OnInitialize(initConfig)
    
    // Global flags
    rootCmd.PersistentFlags().StringVar(&cfgFile, "config", "", "config file (default is $HOME/.my-cli/config.yaml)")
    rootCmd.PersistentFlags().StringVar(&outputFormat, "output", "text", "output format (text|json)")
    rootCmd.PersistentFlags().BoolVarP(&verbose, "verbose", "v", false, "verbose output")
    
    // Syncbin metadata flag (hidden)
    rootCmd.Flags().Bool("syncbin-metadata", false, "Output Syncbin metadata")
    rootCmd.Flags().MarkHidden("syncbin-metadata")
    
    // Add subcommands
    rootCmd.AddCommand(versionCmd)
    rootCmd.AddCommand(configCmd)
    rootCmd.AddCommand(resourceCmd)
}

func initConfig() {
    if cfgFile != "" {
        viper.SetConfigFile(cfgFile)
    } else {
        home, err := os.UserHomeDir()
        if err != nil {
            fmt.Fprintf(os.Stderr, "Error: %v\n", err)
            os.Exit(1)
        }
        
        viper.AddConfigPath(home + "/.my-cli")
        viper.SetConfigType("yaml")
        viper.SetConfigName("config")
    }
    
    viper.AutomaticEnv()
    
    if err := viper.ReadInConfig(); err == nil {
        if verbose {
            fmt.Fprintln(os.Stderr, "Using config file:", viper.ConfigFileUsed())
        }
    }
}

func setupLogger() {
    level := slog.LevelInfo
    if verbose {
        level = slog.LevelDebug
    }
    
    handler := slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{
        Level: level,
    })
    logger = slog.New(handler)
}

// Syncbin metadata implementation
func init() {
    rootCmd.PreRun = func(cmd *cobra.Command, args []string) {
        if metadata, _ := cmd.Flags().GetBool("syncbin-metadata"); metadata {
            outputMetadata()
            os.Exit(0)
        }
    }
}

func outputMetadata() {
    metadata := map[string]interface{}{
        "name":        "my-cli",
        "version":     version,
        "description": "Professional CLI tool following Syncbin standard",
        "author":      "Your Name <email@example.com>",
        "license":     "MIT",
    }
    json.NewEncoder(os.Stdout).Encode(metadata)
}

var (
    version string
    commit  string
    date    string
)

func SetVersion(v, c, d string) {
    version = v
    commit = c
    date = d
}
```

**Step 4: Version Command** (`internal/cli/version.go`)
```go
package cli

import (
    "encoding/json"
    "fmt"
    
    "github.com/spf13/cobra"
)

var versionCmd = &cobra.Command{
    Use:   "version",
    Short: "Print version information",
    Run:   runVersion,
}

func runVersion(cmd *cobra.Command, args []string) {
    if outputFormat == "json" {
        versionInfo := map[string]string{
            "version": version,
            "commit":  commit,
            "date":    date,
        }
        json.NewEncoder(cmd.OutOrStdout()).Encode(versionInfo)
    } else {
        fmt.Fprintf(cmd.OutOrStdout(), "my-cli version %s\n", version)
        fmt.Fprintf(cmd.OutOrStdout(), "  commit: %s\n", commit)
        fmt.Fprintf(cmd.OutOrStdout(), "  built:  %s\n", date)
    }
}
```

**Step 5: Resource Commands** (`internal/cli/resource.go`)
```go
package cli

import (
    "encoding/json"
    "fmt"
    
    "github.com/spf13/cobra"
    "github.com/yourorg/my-cli/internal/core"
)

var resourceCmd = &cobra.Command{
    Use:   "resource",
    Short: "Manage resources",
}

var resourceCreateCmd = &cobra.Command{
    Use:   "create [name]",
    Short: "Create a new resource",
    Args:  cobra.ExactArgs(1),
    RunE:  runResourceCreate,
}

var resourceListCmd = &cobra.Command{
    Use:   "list",
    Short: "List all resources",
    RunE:  runResourceList,
}

func init() {
    resourceCmd.AddCommand(resourceCreateCmd)
    resourceCmd.AddCommand(resourceListCmd)
    
    // Flags for create
    resourceCreateCmd.Flags().String("type", "", "Resource type (required)")
    resourceCreateCmd.MarkFlagRequired("type")
    
    // Flags for list
    resourceListCmd.Flags().String("filter", "", "Filter resources")
}

func runResourceCreate(cmd *cobra.Command, args []string) error {
    name := args[0]
    resourceType, _ := cmd.Flags().GetString("type")
    
    logger.Info("creating resource",
        "name", name,
        "type", resourceType)
    
    // Business logic
    service := core.NewResourceService()
    resource, err := service.Create(name, resourceType)
    if err != nil {
        return fmt.Errorf("failed to create resource: %w", err)
    }
    
    // Output
    if outputFormat == "json" {
        return json.NewEncoder(cmd.OutOrStdout()).Encode(resource)
    }
    
    fmt.Fprintf(cmd.OutOrStdout(), "Resource '%s' created successfully\n", name)
    return nil
}

func runResourceList(cmd *cobra.Command, args []string) error {
    filter, _ := cmd.Flags().GetString("filter")
    
    logger.Info("listing resources", "filter", filter)
    
    service := core.NewResourceService()
    resources, err := service.List(filter)
    if err != nil {
        return fmt.Errorf("failed to list resources: %w", err)
    }
    
    if outputFormat == "json" {
        return json.NewEncoder(cmd.OutOrStdout()).Encode(resources)
    }
    
    fmt.Fprintf(cmd.OutOrStdout(), "Resources:\n")
    for _, r := range resources {
        fmt.Fprintf(cmd.OutOrStdout(), "  - %s (%s)\n", r.Name, r.Type)
    }
    return nil
}
```

**Step 6: Business Logic** (`internal/core/service.go`)
```go
package core

import (
    "fmt"
    "time"
)

type Resource struct {
    Name      string    `json:"name"`
    Type      string    `json:"type"`
    CreatedAt time.Time `json:"created_at"`
}

type ResourceService struct {
    // Add dependencies (DB, API clients, etc.)
}

func NewResourceService() *ResourceService {
    return &ResourceService{}
}

func (s *ResourceService) Create(name, resourceType string) (*Resource, error) {
    // Validation
    if name == "" {
        return nil, fmt.Errorf("name cannot be empty")
    }
    if resourceType == "" {
        return nil, fmt.Errorf("type cannot be empty")
    }
    
    // Business logic
    resource := &Resource{
        Name:      name,
        Type:      resourceType,
        CreatedAt: time.Now(),
    }
    
    // Persist (DB, API, file, etc.)
    // ...
    
    return resource, nil
}

func (s *ResourceService) List(filter string) ([]*Resource, error) {
    // Fetch from storage
    resources := []*Resource{
        {Name: "resource-1", Type: "type-a", CreatedAt: time.Now()},
        {Name: "resource-2", Type: "type-b", CreatedAt: time.Now()},
    }
    
    // Apply filter
    if filter != "" {
        filtered := []*Resource{}
        for _, r := range resources {
            if r.Type == filter {
                filtered = append(filtered, r)
            }
        }
        return filtered, nil
    }
    
    return resources, nil
}
```

### Capability 3: Comprehensive Testing Strategy
**When to use**: Ensuring CLI quality and reliability

**Unit Tests** (`internal/core/service_test.go`)
```go
package core_test

import (
    "testing"
    
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
    "github.com/yourorg/my-cli/internal/core"
)

func TestResourceService_Create(t *testing.T) {
    service := core.NewResourceService()
    
    t.Run("successful creation", func(t *testing.T) {
        resource, err := service.Create("test-resource", "test-type")
        
        require.NoError(t, err)
        assert.Equal(t, "test-resource", resource.Name)
        assert.Equal(t, "test-type", resource.Type)
        assert.False(t, resource.CreatedAt.IsZero())
    })
    
    t.Run("empty name", func(t *testing.T) {
        _, err := service.Create("", "test-type")
        
        require.Error(t, err)
        assert.Contains(t, err.Error(), "name cannot be empty")
    })
    
    t.Run("empty type", func(t *testing.T) {
        _, err := service.Create("test-resource", "")
        
        require.Error(t, err)
        assert.Contains(t, err.Error(), "type cannot be empty")
    })
}

func TestResourceService_List(t *testing.T) {
    service := core.NewResourceService()
    
    t.Run("list all", func(t *testing.T) {
        resources, err := service.List("")
        
        require.NoError(t, err)
        assert.NotEmpty(t, resources)
    })
    
    t.Run("list with filter", func(t *testing.T) {
        resources, err := service.List("type-a")
        
        require.NoError(t, err)
        for _, r := range resources {
            assert.Equal(t, "type-a", r.Type)
        }
    })
}
```

**Integration Tests** (`tests/integration/cli_test.go`)
```go
package integration_test

import (
    "bytes"
    "encoding/json"
    "os/exec"
    "testing"
    
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
)

func TestCLI_Metadata(t *testing.T) {
    cmd := exec.Command("my-cli", "--syncbin-metadata")
    output, err := cmd.Output()
    
    require.NoError(t, err)
    
    var metadata map[string]interface{}
    err = json.Unmarshal(output, &metadata)
    
    require.NoError(t, err)
    assert.Equal(t, "my-cli", metadata["name"])
    assert.NotEmpty(t, metadata["version"])
}

func TestCLI_Version(t *testing.T) {
    t.Run("text output", func(t *testing.T) {
        cmd := exec.Command("my-cli", "version")
        output, err := cmd.Output()
        
        require.NoError(t, err)
        assert.Contains(t, string(output), "my-cli version")
    })
    
    t.Run("json output", func(t *testing.T) {
        cmd := exec.Command("my-cli", "version", "--output", "json")
        output, err := cmd.Output()
        
        require.NoError(t, err)
        
        var versionInfo map[string]string
        err = json.Unmarshal(output, &versionInfo)
        
        require.NoError(t, err)
        assert.NotEmpty(t, versionInfo["version"])
    })
}

func TestCLI_ResourceCreate(t *testing.T) {
    cmd := exec.Command("my-cli", "resource", "create", "test-res", "--type", "test-type")
    output, err := cmd.Output()
    
    require.NoError(t, err)
    assert.Contains(t, string(output), "created successfully")
}

func TestCLI_ExitCodes(t *testing.T) {
    t.Run("success", func(t *testing.T) {
        cmd := exec.Command("my-cli", "version")
        err := cmd.Run()
        
        assert.NoError(t, err)
    })
    
    t.Run("usage error", func(t *testing.T) {
        cmd := exec.Command("my-cli", "resource", "create")
        err := cmd.Run()
        
        require.Error(t, err)
        exitErr, ok := err.(*exec.ExitError)
        require.True(t, ok)
        assert.Equal(t, 2, exitErr.ExitCode())
    })
}
```

### Capability 4: plugin-metadata.yaml Creation
**When to use**: Preparing CLI for Syncbin distribution

**Complete metadata file**:
```yaml
apiVersion: syncbin.dev/v1
kind: PluginMetadata

# Identity
name: my-cli
version: v1.0.0
description: "Professional CLI tool for resource management"
author: "Your Name <email@example.com>"
license: MIT
homepage: "https://github.com/yourorg/my-cli"
repository: "https://github.com/yourorg/my-cli"

# Execution
defaultAlias: my-cli
resolutionStrategy: embedded

# Platform-specific entrypoints
platforms:
  linux-amd64:
    entrypoint: ./bin/my-cli-linux-amd64
    sha256: ""  # Filled by CI/CD
  linux-arm64:
    entrypoint: ./bin/my-cli-linux-arm64
    sha256: ""
  darwin-amd64:
    entrypoint: ./bin/my-cli-darwin-amd64
    sha256: ""
  darwin-arm64:
    entrypoint: ./bin/my-cli-darwin-arm64
    sha256: ""

# Requirements
requiresRoot: false
minSyncbinVersion: v2.0.0

# Optional: Daemon capabilities
capabilities: []

# Optional: Dependencies
dependencies: []

# Tags for discovery
tags:
  - resource-management
  - cli-tool
  - automation

# Categories
categories:
  - development
  - devops
```

### Capability 5: CI/CD Pipeline for Automated Publishing
**When to use**: Automating build, test, and distribution

**GitHub Actions** (`.github/workflows/publish.yml`)
```yaml
name: Build and Publish

on:
  push:
    tags:
      - 'v*'

env:
  REGISTRY: registry.syncbin.dev
  NAMESPACE: community

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Go
        uses: actions/setup-go@v4
        with:
          go-version: '1.21'
      
      - name: Run tests
        run: |
          go test -v -race -coverprofile=coverage.out ./...
          go tool cover -func=coverage.out | grep total | awk '{print $3}'
      
      - name: Check coverage
        run: |
          COVERAGE=$(go tool cover -func=coverage.out | grep total | awk '{print $3}' | sed 's/%//')
          if (( $(echo "$COVERAGE < 60" | bc -l) )); then
            echo "Coverage $COVERAGE% is below 60%"
            exit 1
          fi
      
      - name: Run linters
        uses: golangci/golangci-lint-action@v3
        with:
          version: latest

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Go
        uses: actions/setup-go@v4
        with:
          go-version: '1.21'
      
      - name: Get version
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT
      
      - name: Build multi-platform binaries
        run: |
          mkdir -p dist/bin
          
          # Build flags
          LDFLAGS="-s -w -X main.Version=${{ steps.version.outputs.VERSION }} -X main.Commit=${{ github.sha }} -X main.Date=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
          
          # Linux AMD64
          GOOS=linux GOARCH=amd64 go build -ldflags="$LDFLAGS" -o dist/bin/my-cli-linux-amd64 ./cmd/my-cli
          
          # Linux ARM64
          GOOS=linux GOARCH=arm64 go build -ldflags="$LDFLAGS" -o dist/bin/my-cli-linux-arm64 ./cmd/my-cli
          
          # macOS AMD64
          GOOS=darwin GOARCH=amd64 go build -ldflags="$LDFLAGS" -o dist/bin/my-cli-darwin-amd64 ./cmd/my-cli
          
          # macOS ARM64
          GOOS=darwin GOARCH=arm64 go build -ldflags="$LDFLAGS" -o dist/bin/my-cli-darwin-arm64 ./cmd/my-cli
          
          chmod +x dist/bin/*
      
      - name: Generate checksums
        run: |
          cd dist/bin
          sha256sum * > SHA256SUMS
      
      - name: Copy metadata
        run: |
          cp plugin-metadata.yaml dist/
          cp README.md dist/
      
      - name: Setup ORAS
        uses: oras-project/setup-oras@v1
        with:
          version: 1.1.0
      
      - name: Login to Registry
        run: |
          echo "${{ secrets.REGISTRY_PASSWORD }}" | \
            oras login ${{ env.REGISTRY }} \
            -u "${{ secrets.REGISTRY_USERNAME }}" \
            --password-stdin
      
      - name: Push to OCI Registry
        run: |
          TAG="${{ steps.version.outputs.VERSION }}"
          
          oras push ${{ env.REGISTRY }}/${{ env.NAMESPACE }}/my-cli:${TAG} \
            --artifact-type application/vnd.syncbin.plugin.v1+json \
            --annotation "org.opencontainers.image.title=my-cli" \
            --annotation "org.opencontainers.image.version=${TAG}" \
            --annotation "org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}" \
            --annotation "org.opencontainers.image.created=$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
            --annotation "org.opencontainers.image.revision=${{ github.sha }}" \
            dist/bin/my-cli-linux-amd64:application/octet-stream \
            dist/bin/my-cli-linux-arm64:application/octet-stream \
            dist/bin/my-cli-darwin-amd64:application/octet-stream \
            dist/bin/my-cli-darwin-arm64:application/octet-stream \
            dist/plugin-metadata.yaml:application/yaml \
            dist/README.md:text/markdown \
            dist/bin/SHA256SUMS:text/plain
          
          # Tag as latest
          oras tag ${{ env.REGISTRY }}/${{ env.NAMESPACE }}/my-cli:${TAG} latest
      
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            dist/bin/*
            dist/bin/SHA256SUMS
          body: |
            ## Installation
            ```bash
            syncbin plugin install registry.syncbin.dev/${{ env.NAMESPACE }}/my-cli:${{ steps.version.outputs.VERSION }}
            ```
```

### Capability 6: Complete Documentation
**When to use**: Finalizing CLI for public release

**README.md Template**:
```markdown
# my-cli

Professional CLI tool for resource management following Syncbin standard.

## Installation

### Via Syncbin (Recommended)
```bash
syncbin plugin install registry.syncbin.dev/community/my-cli:latest
```

### Manual Installation
Download the binary for your platform from [releases](https://github.com/yourorg/my-cli/releases).

## Usage

### Basic Commands
```bash
# Show version
my-cli version

# Create a resource
my-cli resource create my-resource --type my-type

# List resources
my-cli resource list

# List with filter
my-cli resource list --filter my-type
```

### Output Formats
```bash
# Human-readable (default)
my-cli resource list

# JSON output
my-cli resource list --output json
```

### Configuration
Create `~/.my-cli/config.yaml`:
```yaml
default_type: my-type
api_endpoint: https://api.example.com
```

## Development

### Prerequisites
- Go 1.21+
- Make

### Build
```bash
make build
```

### Test
```bash
make test
```

### Lint
```bash
make lint
```

## License
MIT
```

## Decision Trees

### Tree 1: CLI Architecture Design
```
START: Design new CLI
│
├─ Simple tool (<5 commands)?
│  └─ YES → Use urfave/cli
│     - Lightweight
│     - Quick setup
│
├─ Complex tool (>5 commands)?
│  └─ YES → Use Cobra
│     - Subcommand support
│     - Auto-generated docs
│
└─ Need advanced features?
   └─ YES → Use Cobra + Viper
      - Configuration management
      - Environment variables
      - Config file support
```

### Tree 2: Testing Strategy
```
START: Implement tests
│
├─ Unit tests (60%+ coverage)
│  - Test business logic
│  - Mock external dependencies
│  - Fast execution
│
├─ Integration tests
│  - Test CLI commands
│  - Test output formats
│  - Test exit codes
│
└─ End-to-end tests
- Test full workflows
- Test with real dependencies
- CI/CD validation
```

### Tree 3: Distribution Strategy
```
START: Distribute CLI
│
├─ Internal use only?
│  └─ YES → Organization namespace
│     - registry.syncbin.dev/org-name/cli
│     - Private access control
│
├─ Community contribution?
│  └─ YES → Community namespace
│     - registry.syncbin.dev/community/cli
│     - Public access
│
└─ Official Syncbin tool?
   └─ YES → Official namespace
      - registry.syncbin.dev/official/cli
      - Curated and signed
```

## Common Patterns

### Pattern 1: Error Handling with Context
```go
func processResource(name string) error {
    if name == "" {
        return fmt.Errorf("resource name cannot be empty")
    }
    
    resource, err := fetchResource(name)
    if err != nil {
        return fmt.Errorf("failed to fetch resource %q: %w", name, err)
    }
    
    if err := validateResource(resource); err != nil {
        return fmt.Errorf("invalid resource %q: %w", name, err)
    }
    
    return nil
}
```

### Pattern 2: Configuration Hierarchy
```go
// Priority: CLI flags > Environment > Config file > Defaults
func loadConfig() (*Config, error) {
    cfg := &Config{
        // Defaults
        APIEndpoint: "https://api.example.com",
        Timeout:     30,
    }
    
    // Config file
    if err := viper.ReadInConfig(); err == nil {
        if err := viper.Unmarshal(cfg); err != nil {
            return nil, err
        }
    }
    
    // Environment variables (MY_CLI_API_ENDPOINT)
    viper.SetEnvPrefix("MY_CLI")
    viper.AutomaticEnv()
    
    // CLI flags override everything
    if viper.IsSet("api-endpoint") {
        cfg.APIEndpoint = viper.GetString("api-endpoint")
    }
    
    return cfg, nil
}
```

### Pattern 3: Structured Output
```go
type OutputWriter struct {
    format string
    writer io.Writer
}

func (w *OutputWriter) Write(data interface{}) error {
    switch w.format {
    case "json":
        return json.NewEncoder(w.writer).Encode(data)
    case "yaml":
        return yaml.NewEncoder(w.writer).Encode(data)
    default:
        return w.writeText(data)
    }
}

func (w *OutputWriter) writeText(data interface{}) error {
    // Custom text formatting
    switch v := data.(type) {
    case *Resource:
        fmt.Fprintf(w.writer, "Name: %s\nType: %s\n", v.Name, v.Type)
    case []*Resource:
        for _, r := range v {
            fmt.Fprintf(w.writer, "- %s (%s)\n", r.Name, r.Type)
        }
    }
    return nil
}
```

## Quality Standards

### Code Quality
- Follow Standard Go Project Layout
- Use structured logging (log/slog)
- All errors wrapped with context
- No hardcoded values (use config)
- GoDoc comments on all exported items
- Consistent naming conventions

### Testing Requirements
- Unit tests: 60%+ coverage
- Integration tests for all commands
- Test all output formats
- Test all exit codes
- Benchmark critical paths
- All tests pass in CI/CD

### Documentation Requirements
- Complete README.md
- Usage examples for all commands
- Configuration documentation
- API documentation (if applicable)
- CHANGELOG.md for versions
- Contributing guidelines

### Performance Standards
- Startup time: <100ms
- Memory usage: <50MB idle
- Command execution: <1s for simple operations
- Concurrent operations supported
- Graceful shutdown

## Troubleshooting Guide

### Issue: Tests failing in CI but passing locally
**Diagnosis**: Environment differences

**Solution**:
```bash
# Run tests with same flags as CI
go test -v -race -coverprofile=coverage.out ./...

# Check for race conditions
go test -race ./...

# Check for timing issues
go test -count=100 ./...
```

### Issue: Binary too large
**Diagnosis**: Debug symbols included

**Solution**:
```bash
# Build with stripped symbols
go build -ldflags="-s -w" -o my-cli ./cmd/my-cli

# Check size
ls -lh my-cli

# Use UPX compression (optional)
upx --best --lzma my-cli
```

### Issue: Config file not loading
**Diagnosis**: Path or format issues

**Solution**:
```go
// Add debug logging
if err := viper.ReadInConfig(); err != nil {
    logger.Debug("config file not found", "error", err)
} else {
    logger.Debug("using config file", "path", viper.ConfigFileUsed())
}
```

## Success Criteria

An agent has mastered this skill when it can:

1. ✅ Design complete CLI architecture from requirements
2. ✅ Implement full CLI with Cobra + Viper
3. ✅ Implement Universal Contract (metadata, output, exit codes)
4. ✅ Write comprehensive tests (60%+ coverage)
5. ✅ Create plugin-metadata.yaml correctly
6. ✅ Set up CI/CD pipeline for automated publishing
7. ✅ Build multi-platform binaries
8. ✅ Package and publish to OCI registry
9. ✅ Write complete documentation
10. ✅ Follow all Syncbin quality standards

## References

### Internal Documentation
- `/standar-clis.md` - Complete Syncbin standard
- `/docs/desarrollo/child-cli.md` - Child CLI development guide
- `/docs/desarrollo/SISTEMA-PLUGINS.md` - Plugin system details
- `syncbin-plugin-engineer` (optional companion skill if present in the host repository)

### External Resources
- [Cobra Documentation](https://cobra.dev/)
- [Viper Documentation](https://github.com/spf13/viper)
- [Standard Go Project Layout](https://github.com/golang-standards/project-layout)
- [Effective Go](https://go.dev/doc/effective_go)
- [Go Testing](https://go.dev/doc/tutorial/add-a-test)

### Tools Required
- Go 1.21+
- Make
- golangci-lint
- ORAS CLI
- Docker/Podman (for testing)

## Version History
- v2.0.0 (2024-03-19): Complete rewrite as full CLI architect skill
- v1.0.0 (2024-03-19): Initial ecosystem overview (deprecated)
