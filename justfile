# Common commands for the expense claims API

# Default: list available recipes
default:
    @just --list

# Build the application (skip tests)
build:
    ./gradlew build -x test

# Run unit/integration tests (H2, no external dependencies)
test:
    ./gradlew test

# Start the compose stack (app + postgres)
up:
    podman compose up --build -d

# Stop the compose stack
down:
    podman compose down

# Stop and wipe database volume
down-clean:
    podman compose down -v

# Run E2E tests against running compose stack
e2e: up
    @echo "Waiting for app to be ready..."
    @timeout 30 bash -c 'until curl -sf http://localhost:8080/api/auth/login > /dev/null 2>&1 || [ $? -eq 22 ]; do sleep 1; done'
    ./gradlew e2eTest
    @echo "E2E tests complete."

# Run all tests (unit + E2E)
test-all: test e2e

# View app logs
logs:
    podman compose logs -f app

# Rebuild and restart just the app container
restart-app:
    podman compose up --build -d app
