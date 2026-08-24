#!/usr/bin/env bash
set -euo pipefail

host_class=""
confirm=""
apply=false
deploy_user=frameflow

while (($#)); do
  case "$1" in
    --host-class) host_class=${2:?}; shift 2 ;;
    --deploy-user) deploy_user=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) echo "usage: $0 --host-class nonprod|production [--deploy-user NAME] [--apply --confirm BOOTSTRAP_CLASS]" >&2; exit 2 ;;
  esac
done
[[ $host_class =~ ^(nonprod|production)$ && $deploy_user == frameflow ]] || {
  echo "invalid host class; the audited deploy/systemd identity is fixed to frameflow" >&2; exit 2;
}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
expected="BOOTSTRAP_$(printf '%s' "$host_class" | tr '[:lower:]' '[:upper:]')"

echo "VPS bootstrap plan ($host_class):"
echo "  install Docker/Compose, Nginx, Certbot, UFW, fail2ban, jq"
echo "  create $deploy_user and environment-scoped /opt + /etc directories"
echo "  allow SSH and Nginx through UFW"
echo "  SSH hardening is NOT auto-installed (anti-lockout gate)"
if ! $apply; then echo "DRY-RUN: host unchanged"; exit 0; fi
[[ $confirm == "$expected" ]] || { echo "refusing apply: --confirm must equal $expected" >&2; exit 2; }
[[ $(id -u) -eq 0 ]] || { echo "bootstrap apply must run as root" >&2; exit 1; }
[[ -r /etc/os-release ]] || { echo "unsupported host: missing /etc/os-release" >&2; exit 1; }
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} =~ ^(22\.04|24\.04)$ ]] || {
  echo "supported baseline is Ubuntu 22.04/24.04; found ${PRETTY_NAME:-unknown}" >&2; exit 1;
}

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates certbot curl docker.io docker-compose-v2 fail2ban jq nginx openssl ufw
id "$deploy_user" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "$deploy_user"
usermod -aG docker "$deploy_user"

install -d -m 0750 -o "$deploy_user" -g "$deploy_user" /opt/frameflow
install -d -m 0750 -o root -g "$deploy_user" /etc/frameflow
install -d -m 0750 -o root -g "$deploy_user" /opt/frameflow/ops /opt/frameflow/ops/releases
install -d -m 0755 /var/www/certbot
if [[ $host_class == nonprod ]]; then
  install -d -m 0750 -o "$deploy_user" -g "$deploy_user" /opt/frameflow/dev /opt/frameflow/staging
  install -d -m 0750 -o root -g "$deploy_user" /etc/frameflow/dev /etc/frameflow/staging
  install -d -m 0770 -o root -g "$deploy_user" /var/backups/frameflow-dev /var/backups/frameflow-staging
  install -d -m 0750 -o www-data -g adm \
    /var/log/nginx/frameflow-dev /var/log/nginx/frameflow-staging
else
  install -d -m 0750 -o "$deploy_user" -g "$deploy_user" /opt/frameflow/production
  install -d -m 0750 -o root -g "$deploy_user" /etc/frameflow/production
  install -d -m 0770 -o root -g "$deploy_user" /var/backups/frameflow-production
  install -d -m 0750 -o www-data -g adm /var/log/nginx/frameflow-production
fi

systemctl enable --now docker fail2ban nginx
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

echo "bootstrap: PASS"
echo "NEXT MANUAL GATE: install an SSH public key for $deploy_user, open a second session,"
echo "then review $script_dir/sshd-hardening.conf, install it, run sshd -t, and reload ssh."
echo "Docker group membership is root-equivalent; restrict this account and never reuse it for app runtime."
