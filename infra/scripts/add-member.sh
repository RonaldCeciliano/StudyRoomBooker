#!/bin/sh
# Add a member to the production database (there is no invitation UI yet).
# Run on the server from anywhere inside the repository, with the Compose stack up:
#
#   infra/scripts/add-member.sh "Jane Doe" jane@uark.edu [jane.uark@uark.edu]
#   infra/scripts/add-member.sh --list
#
# The first email is where sign-in links and reminders go; the optional second is the
# member's UARK email if different. The member can then request a sign-in link.
# Values are passed as psql variables, never pasted into SQL text.
set -eu

cd "$(dirname "$0")/../.."

psql_db() {
    # POSTGRES_USER and POSTGRES_DB come from the db container's own environment.
    docker compose exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q "$@"' psql "$@"
}

usage() {
    echo "usage: $0 \"Full Name\" email [uark-email]" >&2
    echo "       $0 --list" >&2
    exit 2
}

if [ "${1:-}" = "--list" ]; then
    echo "SELECT id, name, email, uark_email, active FROM member ORDER BY id;" | psql_db
    exit 0
fi

if [ $# -lt 2 ] || [ $# -gt 3 ]; then usage; fi
name=$1
email=$2
uark_email=${3:-}
case "$email" in *@*.*) ;; *) echo "Not an email address: $email" >&2; exit 2 ;; esac
if [ -n "$uark_email" ]; then
    case "$uark_email" in *@*.*) ;; *) echo "Not an email address: $uark_email" >&2; exit 2 ;; esac
fi

# Members are an assignment input, so take the shared assignment lock (backend README).
psql_db -v name="$name" -v email="$email" -v uark_email="$uark_email" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(730215011) \gset
INSERT INTO member (name, email, uark_email)
VALUES (btrim(:'name'), btrim(:'email'), nullif(btrim(:'uark_email'), ''))
RETURNING id, name, email, uark_email;
COMMIT;
SQL
