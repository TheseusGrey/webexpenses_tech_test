# webexpenses_tech_test

## Running with Docker/Podman Compose

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) or [Podman](https://podman.io/getting-started/installation) with compose support

### Start the application

```bash
# If using Docker:
docker compose up --build

# For Podman:
podman compose up --build
```

### Stop the application

```bash
# Docker:
docker compose down
podman compose down
```

To also remove the database volume (wipes all data):

```bash
docker compose down -v
podman compose down -v
```

## Manual API Testing

The file `requests.http` contains sample requests for the auth endpoint (valid and invalid cases). Open it in IntelliJ or VS Code (with the REST Client extension) to execute requests directly against a running instance.
