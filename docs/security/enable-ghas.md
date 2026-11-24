# Enable GitHub security features

These settings require a repository administrator and cannot be enabled by
merging files alone. Availability and billing depend on repository visibility,
plan, organization policies, and GitHub security licenses. See
[GitHub's security feature availability](https://docs.github.com/en/get-started/learning-about-github/about-github-advanced-security).

## Secret scanning and push protection

1. Open the repository **Settings → Advanced Security** (under **Security and
   quality**).
2. Under **Secret Protection**, select **Enable**, then confirm **Enable Secret
   Protection**. On a public repository, secret scanning alerts are available
   without a paid license.
3. Under **Secret Protection → Push protection**, select **Enable**. Review
   bypasses and rotate any exposed credentials; push protection detects
   supported secrets, not every possible credential.

See [secret scanning](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/detect-secret-leaks/enable-secret-scanning)
and [push protection](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/prevent-future-leaks/enable-push-protection).

## Code scanning

The committed `.github/workflows/codeql.yml` is **advanced setup**: it analyzes
Java/Kotlin and JavaScript/TypeScript on pull requests, pushes to `main`, and
weekly. GitHub's **default setup** is managed by GitHub instead of this
workflow; do not enable both for the same languages.

1. Open **Settings → Advanced Security → CodeQL analysis**. Enable Code
   Security if required and available.
2. If default setup is already enabled, select the menu next to **CodeQL
   analysis → Switch to advanced**, then **Disable CodeQL**. Do not create a
   second generated workflow; use the workflow committed here.
3. After the workflow is on the default branch, check **Actions → CodeQL** for
   successful runs and **Security → Code scanning** for results. Code scanning
   results cannot be uploaded until the feature is available and enabled.

See [configuring advanced setup](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/configure-code-scanning/configuring-advanced-setup-for-code-scanning).

## Dependency review and Dependabot

Enable the dependency graph in **Settings → Advanced Security** and ensure
dependency review is available for the repository. The pull-request workflow
blocks high and critical vulnerabilities and the GPL-2.0, GPL-3.0, and AGPL-3.0
SPDX variants listed in the workflow. Review this license policy with the
project owner before changing it; unknown licenses are not denied by this list.

Dependabot checks Maven at `/`, npm at `/frontend`, GitHub Actions at `/`, and
Docker at `/` weekly, grouping version updates per ecosystem. The Docker entry
starts working when a root Dockerfile is present. Enable Dependabot alerts and
security updates in **Settings → Advanced Security** as available; security
updates are separate from the grouped version updates.

## Private vulnerability reporting

In **Settings → Advanced Security**, select **Enable** beside **Private
vulnerability reporting**. Confirm that **Security → Advisories → Report a
vulnerability** is available to reporters, as described in `SECURITY.md`.

## Protect `main` with a ruleset

1. Open **Settings → Rulesets → Rulesets → New ruleset → New branch ruleset**.
2. Name it, set **Enforcement status** to **Active**, and under **Target
   branches** choose **Add target → Include default branch** (`main`). Keep
   bypasses limited to necessary administrators or apps.
3. Select **Require a pull request before merging** and **Require status checks
   to pass**. After the workflows have run at least once on a pull request,
   add `ci`, `dependency-review`, `Analyze (java-kotlin)`, and
   `Analyze (javascript-typescript)` as required checks, selecting the GitHub
   Actions source where available. Save with **Create**.
4. Verify a pull request cannot merge while a required check is failing or
   pending. Rulesets enforce checks; merely adding workflow files does not.

See [creating repository rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository).
