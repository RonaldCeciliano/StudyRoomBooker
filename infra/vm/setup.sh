#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu VM (Oracle Cloud Always Free, ARM64 or x86_64).
# See docs/deployment.md. Run as the default user (with sudo), not as root:
#
#   curl -fsSL https://raw.githubusercontent.com/RonaldCeciliano/StudyRoomBooker/main/infra/vm/setup.sh -o setup.sh
#   bash setup.sh studyroombooker.duckdns.org
#
# It is safe to run again. It:
#   1. installs Docker Engine and the Compose plugin from Docker's apt repository;
#   2. opens TCP 80/443 and UDP 443 in the VM's iptables firewall (Oracle's Ubuntu image
#      rejects everything else by default) and saves the rules across reboots;
#   3. clones the repository (or pulls it, if already cloned);
#   4. creates .env from .env.example with a random database password and your hostname,
#      leaving only the Gmail values for you to fill in.
# It does not start the app; it prints the next commands instead.
# The Oracle VCN security list must also allow ingress on 80/443; do that in the console.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/RonaldCeciliano/StudyRoomBooker.git}"
APP_DIR="${APP_DIR:-$HOME/StudyRoomBooker}"
HOSTNAME_ARG="${1:-}"

log() { printf '\n==> %s\n' "$*"; }

if [[ $EUID -eq 0 ]]; then
    echo "Run this as your normal user (it uses sudo where needed), not as root." >&2
    exit 1
fi
if ! grep -qi ubuntu /etc/os-release; then
    echo "This script supports Ubuntu only." >&2
    exit 1
fi

log "Installing Docker Engine and the Compose plugin"
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    sudo apt-get update -q
    sudo apt-get install -y -q ca-certificates curl git
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck disable=SC1091
    codename="$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")"
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $codename stable" \
        | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
    sudo apt-get update -q
    sudo apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
    echo "Docker and Compose are already installed."
fi
sudo systemctl enable --now docker
if ! id -nG "$USER" | grep -qw docker; then
    sudo usermod -aG docker "$USER"
    NEED_RELOGIN=1
fi

log "Opening ports 80/tcp, 443/tcp, and 443/udp in the VM firewall"
open_port() {
    local proto=$1 port=$2
    if sudo iptables -C INPUT -p "$proto" --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; then
        echo "$port/$proto already open"
    else
        # Insert before Oracle's default REJECT rule, or append if there is none.
        local reject
        reject="$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
        if [[ -n "$reject" ]]; then
            sudo iptables -I INPUT "$reject" -p "$proto" --dport "$port" -m state --state NEW -j ACCEPT
        else
            sudo iptables -A INPUT -p "$proto" --dport "$port" -m state --state NEW -j ACCEPT
        fi
        echo "opened $port/$proto"
    fi
}
open_port tcp 80
open_port tcp 443
open_port udp 443
if command -v netfilter-persistent >/dev/null 2>&1; then
    sudo netfilter-persistent save >/dev/null
else
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q iptables-persistent
    sudo netfilter-persistent save >/dev/null
fi

log "Getting the code in $APP_DIR"
if [[ -d "$APP_DIR/.git" ]]; then
    git -C "$APP_DIR" pull --ff-only
else
    git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

log "Preparing .env"
if [[ -f .env ]]; then
    echo ".env already exists; leaving it unchanged."
else
    cp .env.example .env
    chmod 600 .env
    db_password="$(openssl rand -hex 24)"
    sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$db_password|" .env
    if [[ -n "$HOSTNAME_ARG" ]]; then
        sed -i "s|^SITE_ADDRESS=.*|SITE_ADDRESS=$HOSTNAME_ARG|" .env
        sed -i "s|^APP_BASE_URL=.*|APP_BASE_URL=https://$HOSTNAME_ARG|" .env
    fi
    # Default the sender to the SMTP username.
    sed -i "s|^EMAIL_FROM=.*|EMAIL_FROM=|" .env
    echo "Created .env with a random database password."
fi
mkdir -p backups

remaining="$(grep -nE 'example|change-me|abcd-efgh' .env || true)"
log "Done"
if [[ -n "$remaining" ]]; then
    echo "Edit these lines in $APP_DIR/.env (nano .env):"
    echo "$remaining"
    echo
fi
if [[ "${NEED_RELOGIN:-0}" == 1 ]]; then
    echo "You were added to the docker group: log out and back in (or run 'newgrp docker') first."
fi
cat <<EOF
Then start the app:
  cd $APP_DIR
  docker compose up -d --build --wait
  ./infra/scripts/add-member.sh "Your Name" you@uark.edu

Remember: the Oracle VCN security list must allow ingress on TCP 80 and 443.
EOF
