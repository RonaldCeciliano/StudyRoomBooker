#!/bin/sh
# Copies the daily database dumps off the VM to S3-compatible object storage (planned:
# Oracle Object Storage). Runs in the optional `offsite` Compose service; see
# docs/deployment.md "Off-box copy".
#   offsite.sh loop  - default: every OFFSITE_INTERVAL seconds, upload new dumps and
#                      delete remote dumps older than OFFSITE_RETENTION_DAYS
#   offsite.sh once  - do that once now
#   offsite.sh check - list the bucket (verifies credentials)
#   offsite.sh fetch FILE - download FILE from the bucket into /restore (for restores)
# It uploads with `rclone copy`, never `sync`, so losing the local directory can never
# delete the remote copies. Remote cleanup is age-based only.
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
OFFSITE_INTERVAL="${OFFSITE_INTERVAL:-21600}"
OFFSITE_RETENTION_DAYS="${OFFSITE_RETENTION_DAYS:-30}"
# Give the backup service time to write the first dump after a (re)start.
OFFSITE_START_DELAY="${OFFSITE_START_DELAY:-600}"

missing=""
for var in OFFSITE_ENDPOINT OFFSITE_BUCKET OFFSITE_ACCESS_KEY_ID OFFSITE_SECRET_ACCESS_KEY; do
    eval "value=\${$var:-}"
    # shellcheck disable=SC2154 # value is assigned by eval
    [ -n "$value" ] || missing="$missing $var"
done
if [ -n "$missing" ]; then
    echo "offsite: set these in .env:$missing" >&2
    exit 1
fi

# rclone reads the remote definition from the environment; no config file is used.
export RCLONE_CONFIG=""
export RCLONE_CONFIG_REMOTE_TYPE=s3
export RCLONE_CONFIG_REMOTE_PROVIDER=Other
export RCLONE_CONFIG_REMOTE_ENDPOINT="$OFFSITE_ENDPOINT"
export RCLONE_CONFIG_REMOTE_REGION="${OFFSITE_REGION:-}"
export RCLONE_CONFIG_REMOTE_ACCESS_KEY_ID="$OFFSITE_ACCESS_KEY_ID"
export RCLONE_CONFIG_REMOTE_SECRET_ACCESS_KEY="$OFFSITE_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_REMOTE_FORCE_PATH_STYLE=true
# The bucket is created in the console; the keys need not be allowed to create buckets.
export RCLONE_CONFIG_REMOTE_NO_CHECK_BUCKET=true
REMOTE="remote:$OFFSITE_BUCKET"

offsite_once() {
    # Explicit returns: set -e is ignored inside a function called as `offsite_once || ...`.
    rclone copy "$BACKUP_DIR" "$REMOTE" --include 'studyroombooker-*.dump' --stats=0 -v || return 1
    rclone delete "$REMOTE" --include 'studyroombooker-*.dump' --min-age "${OFFSITE_RETENTION_DAYS}d" -v || return 1
    echo "offsite: copy complete; remote has $(rclone lsf "$REMOTE" --include 'studyroombooker-*.dump' | wc -l) dumps"
}

case "${1:-loop}" in
    once)
        offsite_once
        ;;
    check)
        rclone lsl "$REMOTE"
        ;;
    fetch)
        [ -n "${2:-}" ] || { echo "usage: offsite.sh fetch studyroombooker-....dump" >&2; exit 2; }
        rclone copy "$REMOTE/$2" /restore -v
        ;;
    loop)
        echo "offsite: copying $BACKUP_DIR to bucket $OFFSITE_BUCKET every ${OFFSITE_INTERVAL}s after ${OFFSITE_START_DELAY}s, keeping ${OFFSITE_RETENTION_DAYS} days remotely"
        sleep "$OFFSITE_START_DELAY"
        while true; do
            offsite_once || echo "offsite: FAILED; will retry in ${OFFSITE_INTERVAL}s" >&2
            sleep "$OFFSITE_INTERVAL"
        done
        ;;
    *)
        echo "usage: offsite.sh [loop|once|check|fetch FILE]" >&2
        exit 2
        ;;
esac
