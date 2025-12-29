# Change: Add Makefile for Common Development Tasks

**Status: Draft** - Pending review after DevContainer work

## Why
Developers need a simple, consistent way to run common development tasks without remembering complex Gradle commands or project-specific scripts. A Makefile provides a standard interface that works across platforms and makes the project more approachable for new contributors. It also serves as living documentation of available development workflows.

## What Changes
- Create `Makefile` with common development tasks
- Include targets for building, testing, formatting, cleaning, and other common operations
- Include targets for Docker client and Colima setup (install, start, stop, status)
- Document each target with comments
- Ensure Makefile works on macOS, Linux, and Windows (with make available)
- Integrate with existing Gradle build system

## Impact
- Affected specs: New capability `development-workflow` will be created
- Affected code: New `Makefile` file (no existing code to modify)
- Developer workflow: Developers can use `make` commands instead of remembering Gradle syntax

