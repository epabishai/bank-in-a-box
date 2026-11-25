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
| `GET` | `/api/health` | Application liveness only; does not check database health |
| `POST` | `/api/accounts` | Create an account (`name`, `email`, optional `initial_deposit`) |
| `GET` | `/api/accounts/{id}` | Get account details and balance |
| `GET` | `/api/accounts/{id}/transactions?page=1&limit=20` | Get paginated transaction history |
| `POST` | `/api/accounts/{id}/deposit` | Deposit (`amount`, optional `note`) |
| `POST` | `/api/accounts/{id}/withdraw` | Withdraw (`amount`, optional `note`) |

Errors have an `error` message and a `code`; withdrawals exceeding the balance
return `422 INSUFFICIENT_FUNDS`.

## CI failure investigation

[CI Doctor source](.github/workflows/ci-doctor.md) and its generated
[Actions lock file](.github/workflows/ci-doctor.lock.yml) adapt the upstream
[workflow](https://raw.githubusercontent.com/githubnext/agentics/main/workflows/ci-doctor.md)
and [guide](https://raw.githubusercontent.com/githubnext/agentics/main/docs/ci-doctor.md)
from `githubnext/agentics`. **Merging both files to the default branch activates
the workflow** (subject to the prerequisites below); this PR does not constitute
a deployment. It listens only for completed `CI` runs on `main`, and gates
investigation on conclusion `failure`. Successful/cancelled runs do not invoke
the investigator. There is no manual dispatch and no intentional failure test.
If the branch or CI display name changes, update the source trigger and
recompile; do not hand-edit the lock.

The explicit Copilot engine uses read-only GitHub Actions, repository, issue,
and PR tools: `actions_get`/`actions_list`/`get_job_logs` inspect runs and logs,
`get_commit`/`get_file_contents` inspect changes and workflow configuration,
and PR reads/diffs plus issue/PR searches correlate context. It distinguishes
the first meaningful backend/frontend error from the aggregate required `ci`
check, classifies cause and confidence, and reports minimal evidence,
recommended actions, and prevention. Logs, commits, issues, and linked content
are untrusted data, never executable instructions. Shell and editing tools are
explicitly disabled, `cli-proxy: false` retains native MCP access, and
`checkout: false` omits the agent checkout. Compiler-managed setup/activation
still uses trusted workflow configuration and infrastructure shell steps.

Reporting is limited to creating an issue prefixed `[CI failure] ` (max 1) OR
adding an issue comment (max 1), never both by policy. It searches open **and
closed** issues for the same root cause, preferring an open match; otherwise
it comments on the matching closed issue without reopening/changing state.
Only a genuinely distinct cause or no matching issue warrants a new issue.
Comments require a new run/attempt or materially new evidence; duplicates and
non-actionable findings use `noop`. An immediate re-search reduces races;
deduplication and the mutually exclusive reporting choice remain agent policy,
not an atomic uniqueness guarantee. The compiler's default concurrency is
retained (`queue: max`), rather than adding a cancellation policy.

### Prerequisites and authentication

- Enable GitHub Actions and ensure GitHub Agentic Workflows, the Copilot CLI,
  required models, permitted pinned actions/containers, and hosted runners are
  available under the repository/organization's policies.
- Configure the Actions secret `COPILOT_GITHUB_TOKEN` with an eligible Copilot
  credential/subscription. No credential values belong in Markdown or Git.
- The investigation job requests only `actions`, `contents`, `issues`, and
  `pull-requests`: **read**. GitHub MCP authentication uses
  `GH_AW_GITHUB_MCP_SERVER_TOKEN`, then `GH_AW_GITHUB_TOKEN`, then the scoped
  `GITHUB_TOKEN` fallback. Prefer a separate read-only MCP token with those
  repository permissions if an override is needed.
- Default safe-output authentication is the job-scoped `GITHUB_TOKEN`;
  the compiler also supports `GH_AW_GITHUB_TOKEN` as its override. The
  `safe_outputs` job requests only `issues: write`, not contents/PR/discussion
  write. Compiler-managed activation/conclusion jobs also request issue write
  for lifecycle notifications. Do not supply an unnecessarily privileged
  override token.
- `add-comment.target: "*"` permits an existing issue target on `workflow_run`
  (there is no triggering issue). `issues: true`, `pull-requests: false`, and
  `discussions: false` avoid unnecessary write permissions. **v1.2.2 limitation:**
  the issue/PR switches govern permissions, not runtime target-type validation;
  the generated handler does not carry an issue-only type guard. Issue-only
  targeting is also explicit prompt policy, not a proven hard type boundary.

### Installation and scoped compilation

Use the repository's existing compiler version rather than upgrading unrelated
workflows:

```bash
gh extension install github/gh-aw --pin v1.2.2
gh aw add-wizard --help
gh aw compile --help
```

Implementation outcome: GitHub CLI **2.104.0** had no extensions. The standard
extension install failed with HTTP 403 while checking
`https://api.github.com/repos/github/gh-aw/releases/latest`. The official
`v1.2.2/linux-amd64` release asset was downloaded instead, verified against the
release's `checksums.txt` (SHA256
`c2378d85466d29cdbdfebd0e513557b5cbec18d34f44e7cc00c2fa323687a153`),
and registered using `gh extension install .` from
`/tmp/ci-doctor-tools/gh-aw`. `gh aw version` reported **v1.2.2**.
No repositories were cloned.

After consulting help, the requested upstream wizard was invoked in an empty
temporary Git repository with:

```bash
gh aw add-wizard githubnext/agentics/ci-doctor --engine copilot --no-secret --no-gitattributes --no-config
```

It exited 1 with `add-wizard requires an interactive terminal; use 'add' for
non-interactive environments`. It installed nothing, configured no secrets,
created no PR/commit, and ran no workflow. There is no wizard dry-run flag.
The source was adapted manually from the raw upstream `main` files instead.
On an interactive maintainer machine the upstream command is
`gh aw add-wizard githubnext/agentics/ci-doctor`; review its delivery/run
prompts rather than auto-running it against an already installed workflow.

Compile only this source in isolation to avoid discovering custom agents or
regenerating unrelated locks. For example (Git, gh-aw v1.2.2, and ShellCheck
required):

```bash
workdir="$(mktemp -d /tmp/ci-doctor.XXXXXX)"
mkdir -p "$workdir/.github/workflows"
git -C "$workdir" init --initial-branch=main
git -C "$workdir" remote add origin https://github.com/epabishai/bank-in-a-box.git
cp /home/runner/work/bank-in-a-box/bank-in-a-box/.github/workflows/ci-doctor.md "$workdir/.github/workflows/ci-doctor.md"
cd "$workdir"
gh aw compile ci-doctor --no-check-update --strict --action-mode action --action-tag 53d0749e19424a918acd82b4d140264ce48f2f14 --shellcheck
gh aw compile ci-doctor --no-check-update --strict --action-mode action --action-tag 53d0749e19424a918acd82b4d140264ce48f2f14 --no-emit --validate
cp "$workdir/.github/workflows/ci-doctor.lock.yml" /home/runner/work/bank-in-a-box/bank-in-a-box/.github/workflows/ci-doctor.lock.yml
```

The explicit setup-action SHA is the existing v1.2.2 pin; without it the blocked
API lookup left that action mutable. All generated actions are full-SHA pinned
and containers digest pinned. Advisory review found no applicable advisories
for the emitted checkout 7.0.1, download-artifact 8.0.2, github-script 9.0.0,
setup-node 7.1.0, upload-artifact 7.0.2, or gh-aw-actions/setup 1.2.2 pins.
The advisory database query returned the historical download-artifact
`GHSA-cxww-7g56-2vh6` affecting 4.0.0–before 4.1.3, not emitted 8.0.2.
Compiler package queries returned no entries; these checks are not a complete
transitive dependency/container vulnerability audit.

### Validation and limitations

Validation used an isolated `/tmp/ci-doctor-validation` Git repository containing
only this workflow, with temporary `assertions.py` and inert `events.json`
fixtures outside the project. Python **3.12.3**, PyYAML **6.0.1**, and
ShellCheck **0.9.0** were used; no project test infrastructure was added.
Assertions checked both source and generated trigger filters plus the failure
gates: matching CI/main/completed/failure **true**; success, cancelled, wrong
workflow, wrong branch, requested, in-progress, and non-workflow_run event
**false** (eight cases). Other assertions checked read-only investigation
permissions, issue-write-only safe outputs, max 1 and title prefix, arbitrary
issue target configuration, authentication fallback, actual GitHub tool names,
only GitHub/safeoutputs in the Copilot allowlist (no unrestricted tools, shell,
or edit), no agent checkout, immutable pins, no custom-agent metadata,
causal diagnosis, open/closed root-cause matching, confidence/evidence,
new-run/material-evidence dedup, noop, and untrusted-content safety instructions.

Scoped strict compilation, built-in linting, `--shellcheck`, and
`--no-emit --validate` passed with zero warnings. Recompilation was byte-for-byte
idempotent. These are configuration/prompt assertions, **not proof of model
behavior**: live log access, diagnosis accuracy, issue creation, commenting
(including closed-issue recurrence), deduplication, and noop decisions remain
unverified. No live failure was available for replay; no failure was induced
and no workflow was dispatched. After merge, maintainers should observe the
next naturally failing main CI run and inspect the agent trace and issue output.
Java/npm/container validation was not run because application, dependencies,
and container files were untouched.

## Project documentation

- [Product and technical specification](specs/SPEC.md)
- [Architecture Decision Records](docs/adr/README.md): [stack](docs/adr/001-stack.md), [money](docs/adr/002-money.md), [balance concurrency](docs/adr/003-balance-concurrency.md), [SPA serving](docs/adr/004-spa-serving.md), [agent team](docs/adr/005-agent-team.md)
- [OpenAPI](specs/openapi.yaml)
- Design: [tokens](docs/design/tokens.md) and [components](docs/design/components.md)
- Security: [threat model](docs/security/threat-model.md) and [reporting policy](SECURITY.md)
- [Demo script](DEMO-SCRIPT.md)
