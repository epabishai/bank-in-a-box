# ADR-005: Issue-driven agent team workflow

- Status: Accepted
- Date: 2026-11-16

## Context and Problem Statement

Bank in a Box is built from scoped, spec-driven issues by a team of agents.
Work needs to remain traceable, assigned to the appropriate role, and reviewed
before it is merged. The custom role instructions are maintained under
`.github/agents/`.

## Considered Options

- Assign issues to role-specific agents and require a reviewed pull request
- Let agents make untracked changes directly on the default branch
- Use a single general-purpose agent for all issues without role instructions

## Decision

Use the issue as the unit of work and follow this sequence:

1. Define the scope and acceptance criteria in a GitHub issue; identify the
   owning role with an `agent:<role>` label.
2. Assign the issue to the corresponding role-specific agent, which follows
   its instructions in `.github/agents/<role>.agent.md`.
3. The agent implements only that issue's scope and opens a pull request that
   references the issue with `Closes #<number>`.
4. Run the required checks and have the pull request reviewed before merging.
   Record how the acceptance criteria were verified in the pull request.
5. Treat newly discovered, out-of-scope work as a separate issue rather than
   expanding the current change.

## Consequences

- Issues, role instructions, and pull requests provide an auditable connection
  between requirements, implementation, and review.
- Role-specific agents can work in parallel on independent issues while each
  change stays bounded by its acceptance criteria.
- Pull request review and checks remain required; an agent's completion is not
  itself approval to merge.
- The process depends on keeping `.github/agents/` role instructions aligned
  with the repository's spec and issue ownership.
