# Expense Claims API

A Spring Boot backend for an internal expense claim system. Employees submit claims; approvers approve or reject them. Every state change is audited. For steps on transitioning from local compose setup to aws/prod, see [DEPLOY.md](docs/DEPLOY.mdDEPLOY.md).

## Prerequisites

- Java 21
- [Docker](https://docs.docker.com/get-docker/) or [Podman](https://podman.io/getting-started/installation) with compose support
- [just](https://github.com/casey/just) (optional, but recommended)

## Quick Start

```bash
# Start the app + Postgres via compose
just up

# Or without just:
podman compose up --build -d
```

The API is available at `http://localhost:8080`.

## Available Commands

| Command | Description |
|---------|-------------|
| `just build` | Build the application (skip tests) |
| `just test` | Run unit/integration tests (H2, no external deps) |
| `just e2e` | Start compose stack and run E2E tests against it |
| `just test-all` | Run unit tests then E2E tests |
| `just up` | Start the compose stack (app + postgres) |
| `just down` | Stop the compose stack |
| `just down-clean` | Stop and wipe database volume |
| `just logs` | Tail app container logs |
| `just restart-app` | Rebuild and restart the app container |

## Testing

### Unit & Integration Tests

```bash
just test
# or: ./gradlew test
```

### E2E Tests

```bash
just e2e
# or manually:
podman compose up --build -d
./gradlew e2eTest
```

### Manual Testing

There's a `requests.http` file in the project, if you're using `VSCode` or `IntelliJ` you can run the requests defined there to manually test the endpoints via an extension. (You will need a token for endpoints beyondf the auth stage).
