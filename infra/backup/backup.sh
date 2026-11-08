#!/bin/sh
# Daily PostgreSQL backups for the production Compose stack (see docs/deployment.md).
#   backup.sh loop   - default: self-healing; every BACKUP_CHECK_INTERVAL seconds, take a
#                      backup if none exists yet for today (America/Chicago)
#   backup.sh once   - take one backup now
# Dumps use pg_dump's compressed custom format, are verified with pg_restore --list, and
# are written atomically to BACKUP_DIR (a host directory, not the database volume).
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_CHECK_INTERVAL="${BACKUP_CHECK_INTERVAL:-3600}"
# Let the backend apply Flyway migrations before the first dump after a (re)start.
BACKUP_START_DELAY="${BACKUP_START_DELAY:-300}"
DB_HOST="${DB_HOST:-db}"
DB_NAME="${DB_NAME:-studyroombooker}"

backup_once() {
    stamp=$(date +%Y-%m-%dT%H%M%S)
    final="$BACKUP_DIR/studyroombooker-$stamp.dump"
    partial="$BACKUP_DIR/.studyroombooker-$stamp.dump.partial"
    trap 'rm -f "$partial"' EXIT
    pg_dump --host="$DB_HOST" --dbname="$DB_NAME" --format=custom --compress=9 --no-owner --file="$partial"
    pg_restore --list "$partial" >/dev/null
    mv "$partial" "$final"
    trap - EXIT
    echo "backup: wrote $final ($(du -h "$final" | cut -f1))"
    # Rotation: keep BACKUP_RETENTION_DAYS days of daily dumps.
    find "$BACKUP_DIR" -maxdepth 1 -name 'studyroombooker-*.dump' -mtime +"$BACKUP_RETENTION_DAYS" -print -delete \
        | sed 's/^/backup: removed /'
}

case "${1:-loop}" in
    once)
        backup_once
        ;;
    loop)
        echo "backup: checking every ${BACKUP_CHECK_INTERVAL}s after ${BACKUP_START_DELAY}s, keeping ${BACKUP_RETENTION_DAYS} days in $BACKUP_DIR"
        sleep "$BACKUP_START_DELAY"
        while true; do
            today=$(date +%Y-%m-%d)
            if ! ls "$BACKUP_DIR"/studyroombooker-"$today"T*.dump >/dev/null 2>&1; then
                backup_once || echo "backup: FAILED; will retry in ${BACKUP_CHECK_INTERVAL}s" >&2
            fi
            sleep "$BACKUP_CHECK_INTERVAL"
        done
        ;;
    *)
        echo "usage: backup.sh [loop|once]" >&2
        exit 2
        ;;
esac
