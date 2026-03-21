.PHONY: help version clean tidy download build build-all test test-coverage lint fmt run install uninstall

# Variables
GO := go
GOFLAGS := -mod=readonly
BINARY_NAME := applogger-cli
OUTPUT_DIR := bin
VERSION := $(shell git describe --tags --always)
COMMIT := $(shell git rev-parse --short HEAD)
BUILD_TIME := $(shell date -u +'%Y-%m-%dT%H:%M:%SZ')
LDFLAGS := -s -w -X github.com/zuccadev-labs/appLoggers/cli/internal/cli.version=$(VERSION) \
           -X github.com/zuccadev-labs/appLoggers/cli/internal/cli.commit=$(COMMIT) \
           -X github.com/zuccadev-labs/appLoggers/cli/internal/cli.buildTime=$(BUILD_TIME)

# Platforms for cross-compilation
PLATFORMS := linux-amd64 linux-arm64 darwin-amd64 darwin-arm64 windows-amd64

help: ## Show this help
	@echo "AppLogger CLI — Development Makefile"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'
	@echo ""

version: ## Show version info
	@echo "Version: $(VERSION)"
	@echo "Commit: $(COMMIT)"
	@echo "Built: $(BUILD_TIME)"

clean: ## Clean build artifacts
	@rm -rf $(OUTPUT_DIR)
	@$(GO) clean -cache -testcache
	@echo "✓ Clean complete"

tidy: ## Tidy and download Go dependencies
	@$(GO) mod tidy -v
	@$(GO) mod download
	@echo "✓ Dependencies tidy complete"

download: tidy ## Download and verify dependencies
	@$(GO) mod verify
	@echo "✓ Dependencies verified"

build: download ## Build CLI binary (current OS)
	@mkdir -p $(OUTPUT_DIR)
	@echo "Building $(BINARY_NAME) $(VERSION) ($(COMMIT))..."
	@$(GO) build $(GOFLAGS) -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY_NAME) ./cmd/applogger-cli
	@echo "✓ Binary: $(OUTPUT_DIR)/$(BINARY_NAME)"

build-all: download ## Build CLI for all platforms (Linux, macOS, Windows)
	@mkdir -p $(OUTPUT_DIR)
	@echo "Cross-compiling for all platforms..."
	@for platform in $(PLATFORMS); do \
		os=$$(echo $$platform | cut -d'-' -f1); \
		arch=$$(echo $$platform | cut -d'-' -f2); \
		suffix=$$([ "$$os" = "windows" ] && echo ".exe" || echo ""); \
		binary=$(OUTPUT_DIR)/$(BINARY_NAME)-$$platform$$suffix; \
		echo "  → Building for $$platform..."; \
		GOOS=$$os GOARCH=$$arch CGO_ENABLED=0 $(GO) build $(GOFLAGS) \
			-ldflags "$(LDFLAGS)" -o $$binary ./cmd/applogger-cli; \
	done
	@echo "✓ All binaries built in $(OUTPUT_DIR)/"
	@ls -lh $(OUTPUT_DIR)

test: download ## Run unit and integration tests
	@echo "Running tests..."
	@$(GO) test -v -race -coverprofile=coverage.out -covermode=atomic ./...
	@echo "✓ Tests complete"

test-coverage: test ## Run tests with coverage report
	@echo ""
	@echo "Coverage Summary:"
	@$(GO) tool cover -func=coverage.out | tail -1
	@echo ""
	@$(GO) tool cover -html=coverage.out -o coverage.html
	@echo "✓ Coverage report: coverage.html"

lint: download ## Run golangci-lint
	@echo "Running linters..."
	@which golangci-lint > /dev/null || (echo "Installing golangci-lint..." && go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest)
	@golangci-lint run -v ./...
	@echo "✓ Linting complete"

fmt: ## Format code with gofmt and goimports
	@echo "Formatting code..."
	@$(GO) fmt ./...
	@which goimports > /dev/null || (echo "Installing goimports..." && go install golang.org/x/tools/cmd/goimports@latest)
	@goimports -w ./...
	@echo "✓ Formatting complete"

run: build ## Build and run with --help
	@$(OUTPUT_DIR)/$(BINARY_NAME) --help

run-query: build ## Build and run telemetry query example
	@$(OUTPUT_DIR)/$(BINARY_NAME) telemetry query --source logs --limit 5 --output text

run-health: build ## Build and run health check
	@$(OUTPUT_DIR)/$(BINARY_NAME) health --output json

run-version: build ## Build and run version command
	@$(OUTPUT_DIR)/$(BINARY_NAME) version --output json

install: build ## Install CLI binary to /usr/local/bin (Unix-like)
	@echo "Installing $(BINARY_NAME)..."
	@sudo cp $(OUTPUT_DIR)/$(BINARY_NAME) /usr/local/bin/$(BINARY_NAME)
	@sudo chmod +x /usr/local/bin/$(BINARY_NAME)
	@echo "✓ Installed to /usr/local/bin/$(BINARY_NAME)"
	@which $(BINARY_NAME)

uninstall: ## Uninstall CLI binary from /usr/local/bin
	@echo "Uninstalling $(BINARY_NAME)..."
	@sudo rm -f /usr/local/bin/$(BINARY_NAME)
	@echo "✓ Uninstalled"

# CI/CD targets (for GitHub Actions, local runners)
ci-test: download lint test-coverage ## Run full CI pipeline locally
	@echo "✓ CI pipeline complete"

dev: ## Setup development environment (fmt + build + test)
	@echo "Setting up development environment..."
	@$(MAKE) fmt
	@$(MAKE) build
	@$(MAKE) test
	@echo "✓ Development environment ready"

# Help when no target specified
.DEFAULT_GOAL := help
