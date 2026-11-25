# syntax=docker/dockerfile:1
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=secret,id=cfs_feed_token,required=true \
    --mount=type=tmpfs,target=/run/cfs-auth \
    set +x; set -eu; umask 077; \
    token="$(cat /run/secrets/cfs_feed_token)"; \
    test -n "$token"; \
    registry=https://pkgs.dev.azure.com/github-cfs/GitHubCFS/_packaging/github-cfs-oss/npm/registry/; \
    auth_path=//pkgs.dev.azure.com/github-cfs/GitHubCFS/_packaging/github-cfs-oss/npm; \
    printf 'registry=%s\nreplace-registry-host=npmjs\n%s/registry/:_authToken=%s\n%s/:_authToken=%s\n' \
      "$registry" "$auth_path" "$token" "$auth_path" "$token" > /run/cfs-auth/npmrc; \
    unset token; \
    : > /run/cfs-auth/global-npmrc; \
    export NPM_CONFIG_USERCONFIG=/run/cfs-auth/npmrc \
      NPM_CONFIG_GLOBALCONFIG=/run/cfs-auth/global-npmrc \
      NPM_CONFIG_CACHE=/run/cfs-auth/npm-cache \
      NPM_CONFIG_REGISTRY="$registry" \
      NPM_CONFIG_REPLACE_REGISTRY_HOST=npmjs \
      NPM_CONFIG_UPDATE_NOTIFIER=false; \
    npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
COPY --from=frontend /app/frontend/dist ./frontend/dist
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /app/target/bank-in-a-box-1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build --chown=app:app /app/src/main/resources/config.yml /app/config.yml
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1
CMD ["java", "-jar", "/app/app.jar", "server", "/app/config.yml"]
