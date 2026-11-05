# StudyRoomBooker frontend

React + TypeScript single-page app built with Vite. See the root [README](../README.md) and [ARCHITECTURE.md](../ARCHITECTURE.md).

## Commands

```bash
npm install      # install dependencies
npm run dev         # start the dev server at http://localhost:5173 (API proxied to localhost:8080)
npm run dev:mock    # start the dev server with the fake API from src/mocks (no backend needed)
npm test            # run the tests once
npm run test:watch  # re-run tests as files change
npm run build       # type-check and build to dist/
npm run lint        # run oxlint
npm run generate:api  # regenerate src/api/schema.d.ts from ../openapi.yaml
npm run check:api     # fail if schema.d.ts is out of date with the spec
```

## Mock API

[MSW](https://mswjs.io/) fakes the backend in tests and in `npm run dev:mock`. Request handlers live in `src/mocks/handlers.ts` and fake data in `src/mocks/data.ts`. Tests fail if they call an endpoint that has no handler.

## API types

API request and response types are generated from the OpenAPI contract in [`../openapi.yaml`](../openapi.yaml) with [openapi-typescript](https://openapi-ts.dev/) (ADR 0006). The generated `src/api/schema.d.ts` is committed; don't edit it by hand. After the spec changes, run `npm run generate:api` and commit both files. Import types through `src/api/types.ts`, for example `Schemas['Shift']`.

The spec is written by hand for now. Once the Spring Boot backend exists, springdoc-openapi will generate it.
