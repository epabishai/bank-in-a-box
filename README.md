# Bank in a Box

[![CI](https://github.com/epabishai/bank-in-a-box/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/epabishai/bank-in-a-box/actions/workflows/ci.yml)
[![CodeQL](https://github.com/epabishai/bank-in-a-box/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/epabishai/bank-in-a-box/actions/workflows/codeql.yml)
[![License: not specified](https://img.shields.io/badge/license-not%20specified-lightgrey)](https://github.com/epabishai/bank-in-a-box/community/license)

Bank in a Box is a small example banking application with a Java 21 / Dropwizard
API, a React frontend, and PostgreSQL storage. Create accounts, deposit and
withdraw funds, and review transaction history. The repository also demonstrates
how a team can build and secure an application from specification-driven GitHub
issues. For the live walkthrough, see the [demo script](DEMO-SCRIPT.md).

> **Security:** This demo has no authentication or authorization. Do not expose
> it to untrusted networks or use it for real funds or customer data. See the
> [threat model](docs/security/threat-model.md) and [security policy](SECURITY.md).

## Quick start with Docker Compose

Docker Compose starts the app at <http://localhost:8080> and PostgreSQL in a
named volume. Run `docker compose up --build` in an environment where the Docker
build secret `cfs_feed_token` is available. In this repository's package-
firewalled environment, use the CFS wrapper to provide that secret:

```bash
BUILDX_BAKE_ENTITLEMENTS_FS=0 gh cfs -- docker compose up --build
```

The wrapper requires the `gh cfs` extension, Azure CLI 2.54 or later, and an
account authorized for the CFS feed. The app uses development database
credentials by default; override `DB_USER` and `DB_PASSWORD` for other
environments. `docker compose down` stops the services but preserves the
database volume; `docker compose down -v` also deletes its data.

## Local development

Prerequisites: Java 21, Maven, Node.js 24, npm, and Docker Compose for the local
PostgreSQL database. Start the database from the repository root; Compose
publishes PostgreSQL on loopback at `localhost:5432` for the local backend:

```bash
docker compose up -d db
```

In a first terminal, build and run the backend from the repository root:

```bash
mvn -B verify
java -jar target/bank-in-a-box-1.0-SNAPSHOT.jar server src/main/resources/config.yml
```

In a second terminal, start the Vite frontend:

```bash
cd frontend
gh cfs -- npm ci
gh cfs -- npm test
gh cfs -- npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` requests to the backend on
port 8080. If your npm registry is directly accessible, the `gh cfs --`
prefix can be omitted from npm commands. Maven packages the built frontend
from `frontend/dist`; it does not run npm. To build a self-contained JAR,
run `gh cfs -- npm run build` in `frontend/` before `mvn -B clean verify`.
PostgreSQL must be available while the backend runs.

## Architecture

```text
                         Local development
 Browser ──UI──► Vite / React :5173 ──/api proxy──► Dropwizard :8080
                                                       │
                                                       │ JDBI + SQL
                                                       ▼
                                                   PostgreSQL :5432

                         Docker Compose
 Browser ──HTTP──► app container :8080 ──JDBI + SQL──► db container :5432
                         │
                         ├── React static assets (/) and SPA fallback
                         └── REST API (/api)
```

The production image serves both the compiled React application and the REST
API from Dropwizard. PostgreSQL data persists in the Compose named volume.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Dropwizard application HTTP port |
| `ADMIN_PORT` | `8081` | Dropwizard admin and health-check port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bank` | PostgreSQL JDBC URL |
| `DB_USER` | `bank` | Database user (development only) |
| `DB_PASSWORD` | `bank` | Database password (development only) |
| `ENV` | `development` | Dropwizard environment label; it does not configure authentication or CORS |
| `CFS_TOKEN_FILE` | `/dev/null` | Compose build-secret file override for `cfs_feed_token` |

Compose sets `DB_URL` to `jdbc:postgresql://db:5432/bank` for the app container
and uses `DB_USER` / `DB_PASSWORD` for both services. Do not use the development
credentials outside a local demo.

## API

All application endpoints are under `/api` and return JSON. Amounts are decimal
values with at most two fractional digits. See the full
[OpenAPI contract](specs/openapi.yaml) for schemas and error responses.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Application and database health |
| `POST` | `/api/accounts` | Create an account (`name`, `email`, optional `initial_deposit`) |
| `GET` | `/api/accounts/{id}` | Get account details and balance |
| `GET` | `/api/accounts/{id}/transactions?page=1&limit=20` | Get paginated transaction history |
| `POST` | `/api/accounts/{id}/deposit` | Deposit (`amount`, optional `note`) |
| `POST` | `/api/accounts/{id}/withdraw` | Withdraw (`amount`, optional `note`) |

Errors have an `error` message and a `code`; withdrawals exceeding the balance
return `422 INSUFFICIENT_FUNDS`.

## Project documentation

- [Product and technical specification](specs/SPEC.md)
- [Architecture Decision Records](docs/adr/README.md): [stack](docs/adr/001-stack.md), [money](docs/adr/002-money.md), [balance concurrency](docs/adr/003-balance-concurrency.md), [SPA serving](docs/adr/004-spa-serving.md), [agent team](docs/adr/005-agent-team.md)
- [OpenAPI](specs/openapi.yaml)
- Design: [tokens](docs/design/tokens.md) and [components](docs/design/components.md)
- Security: [threat model](docs/security/threat-model.md) and [reporting policy](SECURITY.md)
- [Demo script](DEMO-SCRIPT.md)
