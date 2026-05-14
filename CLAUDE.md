# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
# Build
mvn compile

# Run tests
mvn test

# Package as JAR
mvn package
```

No Maven wrapper is present — use the system `mvn` command.

## Architecture

Standard Maven project with group `com.github.loong`, artifact `smile_cli`. Packaging is JAR (plain Java, no framework).

Source layout:
- `src/main/java/com/github/loong/` — application code
- `src/test/java/com/github/loong/` — tests (JUnit 3.8.1, extending `TestCase`)

## OpenSpec

This project uses OpenSpec for spec-driven development. Slash commands are available under `/opsx:` (explore, new, continue, apply, ff, sync, archive, bulk-archive, verify, onboard). Configuration is in `openspec/config.yaml`.
