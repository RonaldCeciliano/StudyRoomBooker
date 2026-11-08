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

There is no invitation UI yet. Add members on the server with
[`infra/scripts/add-member.sh`](../infra/scripts/add-member.sh); they can then request a
sign-in link at the site:

```sh
infra/scripts/add-member.sh "Jane Doe" jane@uark.edu              # sign-in/reminder email
infra/scripts/add-member.sh "Jane Doe" jane@gmail.com jd@uark.edu # optional separate UARK email
infra/scripts/add-member.sh --list
```

The script takes the shared assignment lock, passes values as psql variables (no SQL
quoting needed), and fails on a duplicate email (case-insensitive). Deactivating a member
is still SQL (`UPDATE member SET active = false WHERE id = ...;`); their existing future
shifts stay assigned, so release or reassign those first.

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

## Backups

The `backup` service (`infra/backup/backup.sh`, using the `postgres:17` image) takes one
`pg_dump` per America/Chicago day. It checks hourly, starting 5 minutes after it starts so
migrations finish first, and retries on the next check if a dump fails. Each dump:

- uses pg_dump's compressed custom format (`--format=custom --compress=9`);
- is verified with `pg_restore --list` and written atomically (no half-written files);
- lands in `BACKUP_HOST_DIR` (default `./backups` next to `docker-compose.yml`), a host
  directory separate from the `postgres-data` volume;
- is deleted after `BACKUP_RETENTION_DAYS` days (default 14).

Check it with `docker compose logs backup` and `ls -lh backups/`. Take one on demand
(before an upgrade, for example) with:

```sh
docker compose exec backup /bin/sh /backup.sh once
```

### Off-box copy

A host directory still lives on the same VM, so the optional `offsite` service
([`infra/backup/offsite.sh`](../infra/backup/offsite.sh), using the `rclone/rclone` image)
copies the dumps to S3-compatible object storage. The plan is Oracle Object Storage
through its S3-compatible API. It is off until you enable it:

1. In the Oracle console (needs Ronald's access): create a private bucket, e.g.
   `studyroombooker-backups`, and note the tenancy's **Object Storage namespace** and the
   region. Under your user profile, create a **Customer secret key**; its access key and
   secret are the S3 credentials.
2. Uncomment and fill in the `OFFSITE_*` lines and `COMPOSE_PROFILES=offsite` in `.env`.
   The endpoint is `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`.
3. Verify the credentials, push the existing dumps, and start the service:

   ```sh
   docker compose run --rm offsite check
   docker compose run --rm offsite once
   docker compose up -d --wait
   ```

Every 6 hours (`OFFSITE_INTERVAL`, starting 10 minutes after start) it uploads new dumps
with `rclone copy` and deletes remote dumps older than `OFFSITE_RETENTION_DAYS` (default
30). It never uses `rclone sync`, so a wiped local `backups/` directory cannot delete the
remote copies. Check it with `docker compose logs offsite`. The keys live only in `.env`.

**Verify the current Always Free Object Storage allowance in the Oracle console before
relying on it.** The total is believed to be around 20 GB, but this has not been verified.
Daily dumps of this database are tens of kilobytes to a few megabytes, so 30 days fits
easily.

### Restore

1. Pick a dump: `ls -lh backups/`.
2. Stop the app so nothing writes during the restore:
   `docker compose stop caddy backend`
3. Recreate the empty database:

   ```sh
   docker compose exec db sh -c 'dropdb -U "$POSTGRES_USER" studyroombooker && createdb -U "$POSTGRES_USER" studyroombooker'
   ```

4. Restore (the `backup` container has the PostgreSQL client and the dumps mounted):

   ```sh
   docker compose exec backup pg_restore --host=db --dbname=studyroombooker --no-owner --exit-on-error \
     /backups/studyroombooker-YYYY-MM-DDTHHMMSS.dump
   ```

5. Start the app: `docker compose start backend caddy`. Flyway applies any newer
   migrations. Users must sign in again, because sessions are not stored in the database.

To restore on a new VM, run `docker compose up -d --wait`, then copy a dump from the
bucket into `backups/` (`docker compose run --rm offsite check` lists them):

```sh
docker compose run --rm -v "$PWD/backups:/restore" offsite fetch studyroombooker-YYYY-MM-DDTHHMMSS.dump
```

then follow steps 2–5.
