# Colima Setup for DevContainer

This guide explains how to set up Colima (Container Linux on Mac) to run DevContainers for this project.

## Prerequisites

- macOS (Colima is primarily for macOS/Linux)
- Homebrew (recommended for installation)

## Installation

### Install Colima

```bash
brew install colima
```

### Install Docker CLI

Colima provides the Docker runtime, but you'll need the Docker CLI:

```bash
brew install docker
```

## Configuration

### Start Colima with Recommended Resources

This project requires sufficient resources for Kotlin compilation and Gradle builds. Start Colima with:

```bash
colima start --cpu 2 --memory 4
```

This allocates:
- **2 CPUs** - For parallel compilation and test execution
- **4GB RAM** - For Kotlin compiler, Gradle daemon, and test execution

### Verify Setup

After starting Colima, verify Docker is working:

```bash
docker ps
```

You should see an empty list (no error messages).

## Usage with DevContainers

Once Colima is running, you can use DevContainers in VS Code or Cursor:

1. Open the project in VS Code/Cursor
2. When prompted, click "Reopen in Container"
3. Or use Command Palette: "Dev Containers: Reopen in Container"

## Troubleshooting

### Colima won't start

- Check available resources: `colima status`
- Ensure you have enough free disk space
- Try restarting: `colima stop && colima start --cpu 2 --memory 4`

### Docker commands fail

- Ensure Colima is running: `colima status`
- Check Docker context: `docker context ls` (should show `colima`)

### Insufficient resources

If builds are slow or fail:
- Increase memory: `colima stop && colima start --cpu 2 --memory 6`
- Increase CPUs: `colima stop && colima start --cpu 4 --memory 4`

## Stopping Colima

When not using DevContainers, you can stop Colima to free resources:

```bash
colima stop
```

To start again later:

```bash
colima start --cpu 2 --memory 4
```

