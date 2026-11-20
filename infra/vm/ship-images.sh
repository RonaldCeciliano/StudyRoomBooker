#!/usr/bin/env bash
# Build the backend and Caddy images on this computer and load them onto the server.
# Needed on a 1 GB VM (VM.Standard.E2.1.Micro), which is too small to compile the app.
# Works on any VM; see docs/deployment.md. Run from your clone, with Docker running:
#
#   infra/vm/ship-images.sh ~/.ssh/oracle-srb.key ubuntu@203.0.113.10
#
# It:
#   1. updates the server's clone (git pull --ff-only) and checks it is at the same commit
#      as this one, so the images match the server's compose file and migrations;
#   2. builds both images for the server's CPU (amd64 or arm64) from this commit;
#   3. streams them over SSH (docker save | gzip | docker load);
#   4. restarts the stack there with `docker compose up -d --no-build --wait`.
# Extra ssh options (for example a port) can go in SSH_OPTS. REMOTE_DIR defaults to
# StudyRoomBooker in the server user's home directory.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <ssh-private-key> <user@host>" >&2
    exit 2
fi
KEY=$1
HOST=$2
REMOTE_DIR="${REMOTE_DIR:-StudyRoomBooker}"
IMAGES=(studyroombooker-backend:latest studyroombooker-caddy:latest)
# shellcheck disable=SC2206 # SSH_OPTS is intentionally split into words
SSH=(ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new ${SSH_OPTS:-} "$HOST")

log() { printf '\n==> %s\n' "$*"; }
cd "$(dirname "$0")/../.."

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "You have uncommitted changes; commit or stash them so the images match a pushed commit." >&2
    exit 1
fi
LOCAL_COMMIT=$(git rev-parse HEAD)

log "Updating the server's copy of the repository"
REMOTE_COMMIT=$("${SSH[@]}" "cd '$REMOTE_DIR' && git pull -q --ff-only && git rev-parse HEAD")
if [[ "$REMOTE_COMMIT" != "$LOCAL_COMMIT" ]]; then
    echo "The server is at ${REMOTE_COMMIT:0:7} but this clone is at ${LOCAL_COMMIT:0:7}." >&2
    echo "Run 'git switch main && git pull' here (or push your commit) and try again." >&2
    exit 1
fi
echo "Both at ${LOCAL_COMMIT:0:7}."

case "$("${SSH[@]}" uname -m)" in
    x86_64) PLATFORM=linux/amd64 ;;
    aarch64 | arm64) PLATFORM=linux/arm64 ;;
    *) echo "Unsupported server CPU." >&2; exit 1 ;;
esac

log "Building images for $PLATFORM"
# Compose requires the production variables to be set even to build; these are unused.
SITE_ADDRESS=unused APP_BASE_URL=unused DB_USERNAME=unused DB_PASSWORD=unused \
SMTP_USERNAME=unused SMTP_PASSWORD=unused DOCKER_DEFAULT_PLATFORM=$PLATFORM \
    docker compose -f docker-compose.yml build backend caddy

log "Sending images to $HOST (this can take a few minutes)"
docker save "${IMAGES[@]}" | gzip -1 | "${SSH[@]}" 'gunzip | docker load'

log "Starting the app on the server"
"${SSH[@]}" "cd '$REMOTE_DIR' && docker compose up -d --no-build --wait && docker compose ps"
"${SSH[@]}" 'docker image prune -f >/dev/null'
log "Done: the server runs ${LOCAL_COMMIT:0:7}"
