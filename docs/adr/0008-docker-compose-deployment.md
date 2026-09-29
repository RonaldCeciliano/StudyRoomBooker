# 0008 — Docker Compose deployment

Status: Documented in the architecture plan; pending team review before implementation.

## Context

The planned production environment is an Oracle Cloud VM with a target operating cost of $0. Both developers need a usable local environment.

## Decision

Use Docker Compose for production containers with Caddy, Spring Boot, and PostgreSQL, serving the React static frontend through Caddy. Use Compose for local development where practical, with at least PostgreSQL available through a development configuration.

## Consequences

Caddy handles HTTPS and same-origin routing. Check container compatibility if the VM uses ARM. Verify free-tier limits and backup retention before production deployment. Create configuration when its milestone begins.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#deployment-plan) is the source of truth.
