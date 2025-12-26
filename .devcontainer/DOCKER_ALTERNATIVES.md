# Alternative Docker Client Options

This project's DevContainer can work with various Docker clients. This document provides guidance for alternatives to Colima.

## Docker Desktop

Docker Desktop is the official Docker client for macOS, Windows, and Linux.

### Installation

- **macOS/Windows**: Download from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
- **Linux**: Follow distribution-specific installation instructions

### Configuration

1. Install Docker Desktop
2. Start Docker Desktop application
3. Ensure Docker is running (check system tray/status bar)
4. Verify: `docker ps` should work without errors

### Resource Allocation

For this project, configure Docker Desktop with:
- **Minimum 4GB RAM** (Settings → Resources → Memory)
- **Minimum 2 CPUs** (Settings → Resources → CPUs)

### Usage

Once Docker Desktop is running, DevContainers work the same as with Colima.

## Podman

Podman is a daemonless container engine that's compatible with Docker commands.

### Installation (macOS)

```bash
brew install podman
```

### Setup

1. Initialize Podman machine:
   ```bash
   podman machine init
   podman machine start
   ```

2. Configure resource allocation:
   ```bash
   podman machine set --cpus 2 --memory 4096
   ```

3. Set up Docker compatibility:
   ```bash
   # Create alias or symlink for docker commands
   # Podman provides docker-compatible CLI
   ```

### Usage with DevContainers

VS Code/Cursor DevContainers may need additional configuration to work with Podman. You may need to:
- Set `DOCKER_HOST` environment variable
- Configure VS Code settings to use Podman socket

## Lima

Lima (Linux on Mac) provides a way to run Linux VMs on macOS, including Docker.

### Installation

```bash
brew install lima
```

### Setup

1. Start Lima with Docker:
   ```bash
   limactl start template://docker
   ```

2. Configure resources in the template or after creation

### Usage

Lima provides Docker through a VM. You'll need to configure your Docker context to point to the Lima instance.

## WSL 2 (Windows)

For Windows users, WSL 2 with Docker is a good option.

### Setup

1. Install WSL 2 and a Linux distribution
2. Install Docker in WSL 2:
   ```bash
   # In WSL 2 terminal
   curl -fsSL https://get.docker.com -o get-docker.sh
   sh get-docker.sh
   ```

3. Configure Docker to start on boot (optional)

### Resource Allocation

Configure WSL 2 resources in `.wslconfig`:
```ini
[wsl2]
memory=4GB
processors=2
```

## General Recommendations

Regardless of which Docker client you use:

1. **Allocate sufficient resources**: Minimum 4GB RAM and 2 CPUs for this project
2. **Verify Docker is running**: `docker ps` should work
3. **Check Docker context**: `docker context ls` shows available contexts
4. **Test DevContainer**: Try opening the project in a DevContainer to verify setup

## Troubleshooting

### DevContainer won't build

- Verify Docker is running: `docker ps`
- Check available resources
- Review Docker logs for errors
- Ensure Docker has enough disk space

### Performance issues

- Increase allocated resources (RAM/CPU)
- Check for other resource-intensive applications
- Consider stopping unused containers: `docker ps -a` and `docker rm <container-id>`

### Platform-specific issues

- **macOS**: Colima or Docker Desktop are recommended
- **Windows**: Docker Desktop or WSL 2 with Docker
- **Linux**: Native Docker installation or Podman

