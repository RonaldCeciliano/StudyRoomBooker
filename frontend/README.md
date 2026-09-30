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
```

## Mock API

[MSW](https://mswjs.io/) fakes the backend in tests and in `npm run dev:mock`. Request handlers live in `src/mocks/handlers.ts` and fake data in `src/mocks/data.ts`. Tests fail if they call an endpoint that has no handler.
