# ADR-004: Serve the SPA from Dropwizard

- Status: Accepted
- Date: 2026-11-16

## Context and Problem Statement

The React application is built separately into static assets, but deployment
should produce one application image. Dropwizard must serve the SPA at `/`,
while keeping the JSON API under `/api`. Client-side routes must continue to
work when opened directly or refreshed. These are the routing requirements in
[`specs/SPEC.md`](../../specs/SPEC.md).

## Considered Options

- Serve built static assets from Dropwizard and add an SPA fallback
- Deploy a separate web server or CDN for the frontend
- Return the SPA document for every request, including API paths

## Decision

Package the Vite build output on the Dropwizard classpath and serve its static
files from the application. Resolve existing assets normally. For an
unmatched `GET` or `HEAD` request outside `/api`, return the packaged
`index.html` so the client-side router can render the requested route. Keep
`/api` routing ahead of the fallback, so unknown API paths retain their API
404 behavior instead of receiving HTML. Do not map the administrative port
through this fallback.

## Consequences

- The frontend and backend are deployed together in the single application
  image described by the product spec.
- Client-side routes work on direct navigation and refresh without server-side
  route implementations.
- API requests remain JSON and cannot accidentally succeed with an HTML
  response.
- Static assets must be included in the application artifact; serving only the
  JAR without the frontend build is not a complete deployment.
- The asset handler must use safe classpath resource resolution, not
  user-controlled filesystem paths.
