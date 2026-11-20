<p align="center">
  <img src="docs/logo.svg?v=2" alt="HQ logo" width="64" />
</p>

<h1 align="center">HQ</h1>

<p align="center">
  A webhook delivery platform: ingest events, queue them, and reliably deliver them to
  subscriber endpoints.
</p>

<p align="center">
  <img alt="Tier" src="https://img.shields.io/badge/tier-0%20prototype-6C5CE0" />
  <img alt="Java" src="https://img.shields.io/badge/java-25-ED8B00?logo=openjdk&logoColor=white" />
  <img alt="Spring Boot" src="https://img.shields.io/badge/spring%20boot-4.1-6DB33F?logo=springboot&logoColor=white" />
  <img alt="React" src="https://img.shields.io/badge/react-19-61DAFB?logo=react&logoColor=black" />
  <img alt="PostgreSQL" src="https://img.shields.io/badge/postgres-17-4169E1?logo=postgresql&logoColor=white" />
  <img alt="Redis" src="https://img.shields.io/badge/redis-cache-DC382D?logo=redis&logoColor=white" />
  <img alt="RabbitMQ" src="https://img.shields.io/badge/rabbitmq-streams-FF6600?logo=rabbitmq&logoColor=white" />
</p>

## Tiered approach

This project is built in tiers. **Tier 0**, this repository, is the prototype: it exists to
validate the core architecture (auth, ingestion, queuing, delivery) end-to-end. **Tier 1** and
**Tier 2** are the production-grade builds that follow, built on what Tier 0 proves out.

## Structure

- **`backend/`**: Spring Boot modular monolith (Java 25), to be split into services later. Runs on
  port `8080`.
  - **Access**: issues and manages API keys, and publishes every change through a transactional
    outbox to the RabbitMQ super stream `apikey.projections`.
  - **Ingestion** (in progress): its own projection of the API keys, built from those events. The
    projection logic exists; the stream consumer and persistence come next.
  - **`common`**: the published event contracts only.
  - There is no authentication yet (Identity is not built): the API key endpoints take the user id
    in the path.
- **`frontend/`**: React Router app (React 19, Tailwind CSS 4, Radix UI). Runs on port `5173`.
  It comes from an earlier iteration; its Google sign-in and user profile calls have no backend
  yet.
- **`docker-compose.yml`**: local infrastructure, as follows.
  - Postgres 17 (container `maindb`).
  - RabbitMQ with the stream plugin.
  - Redis, which the backend does not use yet.

## Documentation

- [Context map](docs/context-map.md)
- [Access: components, interactions and transaction scopes](docs/access-class-diagram.md)
- [Ingestion: the API key projection](docs/ingestion-class-diagram.md)
- [Postman collection for the Access API](docs/postman/HQ-Access.postman_collection.json)

## Prerequisites

- Java 25 (the backend ships its own Maven wrapper, so no separate Maven install is needed)
- Node.js + npm
- Docker (also needed by the integration tests, which use Testcontainers)

## Getting started

### 1. Start infrastructure

```sh
docker compose up -d
```

This starts:

| Service | Address | Credentials and notes |
|---|---|---|
| Postgres | `localhost:5432` | database `postgres`, user `postgres` / `postgres` |
| RabbitMQ | AMQP on `localhost:5672`, streams on `localhost:5552`, management UI on `localhost:15672` | `guest` / `guest` |
| Redis | `localhost:6379` | |

### 2. Apply the database schema

The SQL migrations in `backend/src/main/resources/db.migration/access` are applied by hand, in
order. There is no Flyway; `SqlMigrationValidateIntegrationTest` checks that they match the
entities.

```sh
for f in backend/src/main/resources/db.migration/access/V*.sql; do
  docker exec -i maindb psql -U postgres -d postgres -v ON_ERROR_STOP=1 < "$f"
done
```

On a database that already has some of them, apply only the new ones. The backend also runs
with `spring.jpa.hibernate.ddl-auto=update`, so Hibernate creates the schemas and anything
missing at startup.

### 3. Run the backend

```sh
cd backend
./mvnw spring-boot:run
```

- **API:** `http://localhost:8080`. API keys are under `/api/v1/user/{userId}/apikey` (create,
  get, update, delete); see the Postman collection.
- **Health:** `http://localhost:8080/actuator/health`. It reports DOWN if the outbox relay has
  halted.
- **Outbox relay:** on by default (`access.outbox.relay.*` in `application.properties`). It
  creates the `apikey.projections` super stream on its first tick.
- **Time zone:** the backend runs in UTC.
- **Configuration:** everything is in `backend/src/main/resources/application.properties`.
  There are no profiles.

### 4. Run the tests

```sh
cd backend
./mvnw test
```

Integration tests start their own Postgres and RabbitMQ containers, so Docker must be running.

### 5. Run the frontend

```sh
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` and proxies `/api` to the backend on
`http://localhost:8080` (see `frontend/vite.config.ts`).

## Notes

- Real secrets must never be committed to `backend/src/main/resources/application*.properties`.
  The only credentials there today are the local Docker defaults.
