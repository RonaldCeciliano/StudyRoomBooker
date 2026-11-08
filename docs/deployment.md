# Production deployment

The production stack is `docker-compose.yml` at the repository root (ADR 0008):

```text
Internet -> Caddy :80/:443 -> React static files (/srv)
                           -> backend:8080  for /api/*, /ott/generate, /login/ott, /logout
                                  -> db:5432 (PostgreSQL 17, not published)
```

`docker-compose.dev.yml` remains the local development database only.

## Images

| Service | Image | Notes |
|---|---|---|
| `db` | `postgres:17` | Data in the `postgres-data` volume; `pg_isready` health check. |
| `backend` | built from `infra/backend/Dockerfile` | Temurin 21 JRE, non-root, `prod` profile, `/actuator/health` health check. |
| `caddy` | built from `infra/caddy/Dockerfile` | Builds the React app (Node 22) and serves it with `infra/caddy/Caddyfile`. |

All base images are multi-arch (amd64 and arm64), and nothing pins a platform, so the same
files build natively on the planned ARM64 Oracle VM. Both Dockerfiles build from the
repository root; `.dockerignore` keeps `node_modules`, `target`, `.git`, and `.env` out.

## First deploy

On the VM, with Docker and the Compose plugin installed and ports 80/443 open:

```sh
git clone https://github.com/RonaldCeciliano/StudyRoomBooker.git
cd StudyRoomBooker
cp .env.example .env      # then edit every value; .env is git-ignored
chmod 600 .env
docker compose up -d --build --wait
```

`.env` values (the committed `.env.example` holds fake ones only):

| Variable | Purpose |
|---|---|
| `SITE_ADDRESS` | Public hostname, e.g. the DuckDNS name (#50). Caddy obtains its HTTPS certificate. |
| `APP_BASE_URL` | `https://` + that hostname; used in magic-link and reminder emails. |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL credentials, set when the volume is first created. |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Project Gmail address and app password (#49). |
| `EMAIL_FROM` | Optional; defaults to `SMTP_USERNAME`. |

Compose refuses to start if a required variable is missing, and the backend refuses to
start without SMTP credentials or `APP_BASE_URL`.

## Adding members

There is no invitation UI yet. Add members directly; they can then request a sign-in link:

```sh
docker compose exec db psql -U "$DB_USERNAME" -d studyroombooker \
  -c "INSERT INTO member(name, email) VALUES ('Name', 'name@uark.edu');"
```

Rooms and library hours are also inserted with SQL (`room`, `library_hours` tables;
see `V1__core_planning_schema.sql`). Use the real library hours and room codes.

## Updating

```sh
git pull
docker compose up -d --build --wait
```

Flyway applies new migrations on backend start. Sessions are held in memory, so a backend
restart signs everyone out.

## Health and logs

- `docker compose ps` shows health; Caddy starts only after the backend is healthy.
- `docker compose logs -f backend` (or `caddy`, `db`). Containers use the `json-file`
  driver capped at 5 × 10 MB per service. The backend logs one line per scheduler run that
  did work (`Notifications: RunResult[...]`) and warns on failed sends.
- `/actuator/health` is checked inside the container and is not routed publicly.
  Swagger UI and `/v3/api-docs` are not routed through Caddy either.

## Security notes

- Only Caddy publishes ports. PostgreSQL and the backend are reachable only on the
  Compose network.
- Caddy sets HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, and
  `Referrer-Policy: no-referrer` (the magic-link page URL contains the sign-in token).
- Session cookies are `Secure` under `prod`, so the app must be used over HTTPS.

## Local smoke test

To try the production images without a domain, override the published port and serve plain
HTTP (sign-in will not persist because the session cookie is `Secure`):

```sh
cat > /tmp/smoke.yml <<'YAML'
services:
  caddy:
    ports: !override
      - "18080:80"
YAML
SITE_ADDRESS=:80 APP_BASE_URL=http://localhost:18080 DB_USERNAME=smoke DB_PASSWORD=smoke \
SMTP_USERNAME=fake@example.test SMTP_PASSWORD=fake \
docker compose -p srb-smoke -f docker-compose.yml -f /tmp/smoke.yml up -d --build --wait
curl http://localhost:18080/api/session
docker compose -p srb-smoke down -v
```

Backups are tracked separately (#48).
