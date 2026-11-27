#!/usr/bin/env bash
# Nightly database dump into ~/backups, keeping 14 days. Installed as a cron job (see README.md).
set -euo pipefail
cd "$(dirname "$0")"

dir="$HOME/backups"
mkdir -p "$dir"
file="$dir/cozy-$(date +%F).sql.gz"

docker compose exec -T db pg_dump -U cozy -d cozy | gzip > "$file.tmp"
mv "$file.tmp" "$file"
find "$dir" -name 'cozy-*.sql.gz' -mtime +14 -delete
