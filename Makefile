.PHONY: help build test test-unit test-integration test-e2e clean format check docker-install colima-install colima-start colima-stop colima-status
.DEFAULT_GOAL := help

## help: Display available make targets
help:
	@echo "Available targets:"
	@echo ""
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/^## /  make /' | sed 's/: / - /'
	@echo ""

## build: Build the project
build:
	./gradlew build

## test: Run all tests
test:
	./gradlew test

## test-unit: Run unit tests only
test-unit:
	./gradlew testUnit

## test-integration: Run integration tests only
test-integration:
	./gradlew testIntegration

## test-e2e: Run end-to-end tests only
test-e2e:
	./gradlew testE2e

## clean: Clean build artifacts
clean:
	./gradlew clean

## format: Auto-format code with ktlint
format:
	./gradlew ktlintFormat

## check: Check code style with ktlint
check:
	./gradlew ktlintCheck

## docker-install: Install Docker CLI via Homebrew (macOS)
docker-install:
	brew install docker

## colima-install: Install Colima via Homebrew (macOS)
colima-install:
	brew install colima

## colima-start: Start Colima with 2 CPUs and 4GB RAM
colima-start:
	colima start --cpu 4 --memory 12

## colima-stop: Stop Colima
colima-stop:
	colima stop

## colima-status: Check Colima status
colima-status:
	colima status
