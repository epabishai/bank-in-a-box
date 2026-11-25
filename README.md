# Bank in a Box — a repeatable GitHub platform demo

A small bank (Dropwizard + React + PostgreSQL) **built by a team of agents** from a backlog of issues, orchestrated by GitHub Agentic Workflows and the Copilot cloud agent, then secured with GitHub Advanced Security and released with provenance.

This repo ships the **team and the plan**, not the app. The app appears as agents close issues. Reset and rerun any time.

## What this demonstrates

| Area | Where you see it |
|---|---|
| **Core** — issues, dependencies, labels, PRs, rulesets, required checks | 25 spec-driven issues with `Blocked by` links; branch ruleset requiring the `ci` check |
| **Workflows** — Actions and Agentic Workflows | `.github/workflows/ci.yml` (CI), `orchestrator.md` and `standup.md` (agentic); CodeQL and release (built by agents) |
| **Copilot** — cloud agent, custom agents, instructions | `.github/agents/*.agent.md`, `.github/copilot-instructions.md`, issues assigned to Copilot |
| **GitHub Advanced Security** | CodeQL, dependency review, Dependabot, secret scanning push protection (issue 21) |
| **Supply chain** | GHCR image, SBOM, build provenance attestation (issue 24) |
| **Universe / new releases** | Agentic Workflows GA (Oct 28, 2026) is wired in. Add further announcements to the slot in `DEMO-SCRIPT.md` |

## How it works

```
 specs/backlog.yaml ──create_issues.py──► GitHub issues (labels, "Blocked by #N")
                                              │
        close issue / merge PR / every 2h     ▼
 ┌──────────────────────────┐   assigns ≤2   ┌────────────────────────────┐
 │ orchestrator (agentic wf)│───────────────►│ Copilot cloud agent        │
 │ reads graph, unblocks,   │  + role label  │ follows .github/agents/<role> │
 └──────────────────────────┘                └──────────────┬─────────────┘
          ▲                                                  │ opens PR "Closes #N"
          │                                                  ▼
          └──────── merge ◄── CI + CodeQL + dependency review + human review
```


### The agent team
| Agent | Owns | Issues |
|---|---|---|
| architect | ADRs, OpenAPI | 1, 2 |
| designer | design system specs | 12 |
| backend | Dropwizard, ledger, DB | 3, 5–11, 17 |
| frontend | React UI | 13–16 |
| qa | unit, integration, a11y tests | 18–20 |
| security | GHAS as code, threat model | 21, 22 |
| devops | CI, Docker, release | 4, 23, 24 |
| docs | README and guides | 25 |

### Dependency waves
```
W1  1 ADRs ─► 2 OpenAPI        3 skeleton ─► 4 CI, 5 schema
W2  6 domain ─► 8 accounts ─► 9 deposit/withdraw ─► 10 history   (7 errors, 11 transfers*)
W3  12 design ─► 13 scaffold ─► 14 home ─► 15 dashboard ─► 16 history UI    17 serve SPA
W4  18 unit  19 integration  20 frontend tests  21 GHAS  22 threat model
W5  23 Docker ─► 24 release    25 docs
Day-2 (live, optional)  26 overdraft protection  27 CSV export
```
\* stretch

## Prerequisites

- GitHub repo on github.com with Copilot enabled (Agentic Workflows GA covers Free, Team and Enterprise; the Copilot engine needs a Copilot license)
- `gh` CLI, `python3` with `pip install pyyaml`
- Two secrets (you create them; never commit values):
  - `COPILOT_GITHUB_TOKEN` — lets the Copilot engine run agentic workflows
  - `GH_AW_AGENT_TOKEN` — fine-grained PAT with issues and pull requests read/write, used by `assign-to-agent`
- Repo settings: Actions allowed to create PRs; Copilot cloud agent enabled for the repo

## Run it

### Frontend development

The frontend is in `frontend/`. Run `gh cfs -- npm ci` and `gh cfs -- npm run dev` from that
directory; Vite proxies `/api` requests to the local backend on port 8080.
Inter and JetBrains Mono are loaded from Google Fonts (fonts.googleapis.com).

Package-manager commands in the package-firewalled local environment use
`gh cfs --`. Install the `gh cfs` extension and Azure CLI >=2.54, sign in
with an account authorized for the CFS feed, and check `gh cfs doctor --auth`
before installing dependencies.

```bash
gh repo create my-org/bank-in-a-box --private --source . --push
./scripts/bootstrap.sh my-org/bank-in-a-box
gh aw run orchestrator
```

Then watch: issue 1 (ADRs) is the only unblocked issue, so it goes first. After you merge it, issues 2, 3 and 12 unblock and the orchestrator starts two at a time.

### Build the complete application

With Java 21 and Node.js installed, build the frontend **before** packaging Java:

```bash
cd frontend
gh cfs -- npm ci
gh cfs -- npm test
gh cfs -- npm run build
cd ..
mvn -B clean verify
java -jar target/bank-in-a-box-1.0-SNAPSHOT.jar server src/main/resources/config.yml
```

Maven copies `frontend/dist` into classpath `assets` in the shaded JAR; it does
not run npm. Backend-only tests use test fixtures and do not require a frontend
build, but a deployable artifact does. Rebuild with `clean` after changing the
frontend to remove stale hashed assets. PostgreSQL must be available to run the app.

The application port serves static files and falls back to `index.html` for
unknown non-API GET/HEAD paths. The index (including fallback) uses `no-cache`;
hash-named assets use a one-year immutable cache. Other methods keep JSON errors.
The `/api` namespace and administrative port are not routed through the SPA.

### Run with Docker Compose

Build and start the application and PostgreSQL with
`BUILDX_BAKE_ENTITLEMENTS_FS=0 gh cfs -- docker compose up --build -d`,
then open `http://localhost:8080`. PostgreSQL data is kept in a named volume
across `docker compose down` and subsequent starts. Use `docker compose down -v`
to remove the database volume. Set `DB_USER` and `DB_PASSWORD` in the shell or a
local `.env` file to override the development credentials; these values are
passed to the containers at runtime, so deployment-specific credentials are not
included in the app image.

The wrapper supplies a BuildKit `cfs_feed_token` secret. The frontend install
uses the CFS npm registry with temporary credentials and a cache on tmpfs;
credentials are not stored in image layers. `gh` runs on the host, not inside
the image. For a direct image build, use
`gh cfs -- docker build -t bank-in-a-box:local .`.
Compose builds through Buildx Bake, which otherwise refuses to read the
temporary secret file outside the project; `BUILDX_BAKE_ENTITLEMENTS_FS=0`
grants that read for the wrapped build.
Use plain `docker compose logs` and `docker compose down` for non-build operations.
An uncached build without the wrapper fails because the feed secret is missing
or empty; starting an already-built image needs no feed credentials:
`docker compose up --no-build -d`.

### CI

The required `ci` check runs on pull requests and pushes to `main`. It requires the Java 21 Maven verification and, when `frontend/package.json` exists, the Node.js 24 frontend install, Vitest tests, and build. Download any generated test reports from the workflow run’s **Artifacts** section, including reports from failing tests.

API errors use JSON objects with `error` and `code`. In addition to the domain
codes in the spec, unmatched API routes return `NOT_FOUND`, and other
framework-generated client errors use `HTTP_ERROR` (with the original HTTP
status). Server failures use `INTERNAL_ERROR` without exposing failure details.

## Reset for the next demo

```bash
gh repo delete my-org/bank-in-a-box --yes     # or create a fresh repo from this template
```
Mark this repo as a **template repository** so each run starts clean.

## Known caveats

- The `.md` workflows have not been compiled here. Run `gh aw compile`, fix any frontmatter the compiler rejects, and commit the generated `.lock.yml` files. Frontmatter changes always need a recompile.
- `assign-to-agent` can set a default custom agent but may not choose one per issue. The orchestrator therefore also comments the role, and `.github/copilot-instructions.md` tells Copilot to follow the `agent:` label. Confirm which behaviour your gh-aw version has.
- The app has **no authentication** by design. The threat model says so.
- JVM images are larger than the Go original's 50 MB target; this spec uses 250 MB.
