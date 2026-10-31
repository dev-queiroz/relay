#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
backup="${1:-relay-backup.dump}"
pg_dump --format=custom --no-owner --file "$backup" "$DATABASE_URL"
pg_restore --list "$backup" >/dev/null
echo "Verified backup: $backup"
