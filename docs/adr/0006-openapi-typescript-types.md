# 0006 — TypeScript API types generated from OpenAPI

Status: Documented in the architecture plan; pending team review before implementation.

## Context

The Java backend and TypeScript frontend need a synchronized REST contract.

## Decision

Document the REST contract with OpenAPI, commit the specification to the repository, and generate frontend TypeScript API types using `openapi-typescript`. The planned backend stack includes `springdoc-openapi`.

## Consequences

API contract and OpenAPI setup are part of Milestone 0. Generated types help keep the frontend and backend synchronized as implementation progresses.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#openapi) is the source of truth.
