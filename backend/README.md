# Backend foundation

Requires Java 21, Docker with Compose, and a running Docker engine for integration tests.
The Maven Wrapper supplies Maven. On macOS, select Java 21 before running commands:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

From the repository root, start local PostgreSQL:

```sh
docker compose -f docker-compose.dev.yml up -d --wait
```

Then from `backend/`, run the application with the explicit development profile:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The database is `studyroombooker` on `localhost:5432`. The development-only defaults
are user `studyroombooker_dev` and password `fake-development-password`.
`DB_USERNAME` and `DB_PASSWORD` may override both Compose and the application;
export the same values in both shells. `DB_URL` overrides the application's JDBC URL.
Never commit real credentials. Existing volume credentials persist across restarts.

Stop the database from the repository root with:

```sh
docker compose -f docker-compose.dev.yml down
```

The named volume preserves local data. PostgreSQL 17 is used in development and tests;
no architecture-specific platform is forced, allowing native ARM64 and x86_64 images.

Run tests from `backend/`:

```sh
./mvnw test
```

Integration tests extend `PostgresIntegrationTest`, which activates the `test` profile
and imports `TestcontainersConfiguration`. Spring Boot supplies connection details
from an isolated PostgreSQL container; tests do not use the development database.
The existing `TestBackendApplication` launcher can also run with this container setup.
Docker must be available; tests fail rather than silently skip database coverage.

The application Clock and database sessions use `America/Chicago`. Inject `Clock`
into future time-dependent services; tests can supply a primary `Clock.fixed(...)`
bean for deterministic time. No global JVM timezone is changed.

The entry point and existing dependencies remain unchanged. The `config`, `controller`,
`service`, and `repository` packages establish the conventional layers. Only foundation
configuration exists; the initial Flyway domain migration remains Together-owned.
Hibernate schema generation is disabled in development/tests so it cannot substitute
for that migration. Flyway remains enabled.
