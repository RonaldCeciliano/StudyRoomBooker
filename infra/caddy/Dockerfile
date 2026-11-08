# syntax=docker/dockerfile:1
# Caddy serving the built React app as static files. Multi-arch base images; build from
# the repo root:  docker build -f infra/caddy/Dockerfile .
FROM node:22-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/frontend/dist /srv
