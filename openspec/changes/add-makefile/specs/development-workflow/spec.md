## ADDED Requirements

### Requirement: Makefile for Common Tasks
The project SHALL provide a Makefile with targets for common development tasks.

#### Scenario: Developer runs make build
- **WHEN** a developer runs `make build`
- **THEN** the project is built using Gradle
- **AND** the build output is displayed
- **AND** the command succeeds if the build succeeds

#### Scenario: Developer runs make test
- **WHEN** a developer runs `make test`
- **THEN** all tests are executed using Gradle
- **AND** test results are displayed
- **AND** the command succeeds if all tests pass

#### Scenario: Developer runs make help
- **WHEN** a developer runs `make` or `make help`
- **THEN** a list of available targets is displayed
- **AND** each target includes a brief description
- **AND** the output is formatted for readability

#### Scenario: Developer runs make format
- **WHEN** a developer runs `make format`
- **THEN** code is formatted using ktlint
- **AND** formatting changes are applied to the codebase
- **AND** the command succeeds if formatting completes

#### Scenario: Developer runs make check
- **WHEN** a developer runs `make check`
- **THEN** code style is checked using ktlint
- **AND** violations are reported
- **AND** the command fails if violations are found

#### Scenario: Developer runs make clean
- **WHEN** a developer runs `make clean`
- **THEN** build artifacts are removed using Gradle clean
- **AND** the project is in a clean state
- **AND** the command succeeds

#### Scenario: Developer installs Docker client
- **WHEN** a developer runs `make docker-install`
- **THEN** Docker CLI is installed via Homebrew (on macOS)
- **AND** the installation completes successfully
- **AND** Docker commands are available in the PATH

#### Scenario: Developer installs Colima
- **WHEN** a developer runs `make colima-install`
- **THEN** Colima is installed via Homebrew (on macOS)
- **AND** the installation completes successfully
- **AND** Colima commands are available in the PATH

#### Scenario: Developer starts Colima with recommended resources
- **WHEN** a developer runs `make colima-start`
- **THEN** Colima starts with 2 CPUs and 4GB RAM allocated
- **AND** Docker becomes available for use
- **AND** the command succeeds if Colima starts successfully

#### Scenario: Developer stops Colima
- **WHEN** a developer runs `make colima-stop`
- **THEN** Colima stops running
- **AND** resources are freed
- **AND** the command succeeds

#### Scenario: Developer checks Colima status
- **WHEN** a developer runs `make colima-status`
- **THEN** the current Colima status is displayed
- **AND** information about running state and resources is shown
- **AND** the command succeeds

