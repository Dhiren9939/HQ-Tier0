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
  <img alt="RabbitMQ" src="https://img.shields.io/badge/rabbitmq-queue-FF6600?logo=rabbitmq&logoColor=white" />
</p>

## Tiered approach

This project is built in tiers. **Tier 0**, this repository, is the prototype: it exists to
validate the core architecture (auth, ingestion, queuing, delivery) end-to-end. **Tier 1** and
**Tier 2** are the production-grade builds that follow, built on what Tier 0 proves out.

## Structure

- **`api/`** — Spring Boot service (Java 25). Handles auth (Google OAuth2 login via a
  cookie-based BFF flow with JWT access tokens and multi-device refresh sessions), user
  profiles, and API key management. Runs on port `8081`.
- **`ingestion/`** — Spring Boot service. Accepts incoming events/webhooks and hands them off
  for delivery via RabbitMQ.
- **`delivery/`** — Spring Boot service. Consumes queued events and delivers them to subscriber
  endpoints (with retries, signing, etc.).
- **`frontend/`** — React Router app (React 19, Tailwind CSS 4, Radix UI). Runs on port `5173`.
- **`docker-compose.yml`** — Local infrastructure: Postgres 17 (`maindb`), Redis, and RabbitMQ
  (the event queue between ingestion and delivery).

## Prerequisites

- Java 25 (each service ships its own Maven wrapper, so no separate Maven install is needed)
- Node.js + npm
- Docker

## Getting started

### 1. Start infrastructure

```sh
docker compose up -d
```

This starts Postgres (`localhost:5432`, db `maindb`), Redis (`localhost:6379`), and RabbitMQ
(`localhost:5672`, management UI on `localhost:15672`).

### 2. Apply the database schema

`api` uses `spring.jpa.hibernate.ddl-auto=none`, so the schema is applied by hand:

```sh
psql -h localhost -U postgres -d maindb -f api/schema/root.sql
```

### 3. Configure the API

```sh
cd api
cp .env.example .env
```

Fill in `.env`:

- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — Google OAuth2 client credentials
- `JWT_SECRET` — a base64-encoded 256-bit HMAC key, e.g. `openssl rand -base64 32`

### 4. Run the API

```sh
cd api
./mvnw spring-boot:run
```

The API listens on `http://localhost:8081`. API docs are served under `/api/public` (which is
permitAll in `SecurityConfig`):

- Swagger UI: `http://localhost:8081/api/public/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/api/public/v3/api-docs`

### 5. Run the frontend

```sh
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` and expects the API's post-login redirect to land
on `/dashboard` (configured via `app.frontend-post-login-url` / `app.frontend-login-url` in
`api/src/main/resources/application-dev.properties`).

## Notes

- Real secrets must never be committed to `api/src/main/resources/application*.properties`;
  they're injected via `api/.env` (git-ignored).
