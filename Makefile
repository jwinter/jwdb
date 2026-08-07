.PHONY: help build test test-unit test-integration test-e2e clean format check gradle build-docker test-docker check-docker docker-install colima-install colima-start colima-stop colima-status
.DEFAULT_GOAL := help

# Container runner for hosts without a local JDK, using the same JDK as
# .devcontainer/Dockerfile.
#
# Commands run via `docker exec` in a long-lived container rather than a fresh
# `docker run` each time, so the Gradle daemon stays warm between invocations
# instead of paying JVM and daemon startup on every command. The Gradle cache
# lives in a named volume so dependencies survive the container being removed.
JDK_IMAGE ?= eclipse-temurin:21-jdk
GRADLE_CACHE ?= jwdb-gradle
DEV_CONTAINER ?= jwdb-dev
DOCKER_GRADLE = $(MAKE) --no-print-directory dev-up && docker exec -w /workspace $(DEV_CONTAINER) ./gradlew

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

## dev-up: Start the long-lived build container (idempotent)
dev-up:
	@docker inspect -f '{{.State.Running}}' $(DEV_CONTAINER) 2>/dev/null | grep -q true || { \
		docker rm -f $(DEV_CONTAINER) >/dev/null 2>&1 || true; \
		docker run -d --name $(DEV_CONTAINER) \
			-v "$(CURDIR)":/workspace -w /workspace \
			-v $(GRADLE_CACHE):/root/.gradle \
			$(JDK_IMAGE) sleep infinity >/dev/null; \
	}

## dev-down: Stop and remove the build container (Gradle cache volume is kept)
dev-down:
	@docker rm -f $(DEV_CONTAINER) >/dev/null 2>&1 || true
	@echo "Removed $(DEV_CONTAINER) (cache volume $(GRADLE_CACHE) retained)"

## gradle: Run any Gradle task in a container, e.g. make gradle ARGS="koverVerify"
gradle:
	@$(DOCKER_GRADLE) $(ARGS)

## build-docker: Build the project in a container
build-docker:
	$(DOCKER_GRADLE) build

## test-docker: Run all tests in a container
test-docker:
	$(DOCKER_GRADLE) test

## check-docker: Check code style in a container
check-docker:
	$(DOCKER_GRADLE) ktlintCheck

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
