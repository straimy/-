#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "Run as root: sudo bash bootstrap_ubuntu.sh" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git ufw openssl unzip

install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.asc ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi
. /etc/os-release
cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'GGO HTTP/ACME'
ufw allow 443/tcp comment 'GGO HTTPS'
ufw allow 24842/tcp comment 'GGO game server'
ufw --force enable

mkdir -p /opt/ggo/{infra,backups}
chmod 750 /opt/ggo

echo
printf '%s\n' 'GGO single-node host bootstrap complete.'
printf '%s\n' 'Next: copy infra/vds contents to /opt/ggo/infra and run ./deploy_all.sh.'
printf '%s\n' 'Do not expose PostgreSQL or Redis ports publicly.'
