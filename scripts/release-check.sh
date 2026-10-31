#!/usr/bin/env bash
set -euo pipefail
./mvnw --batch-mode verify
docker build --tag relay:verify .
git diff --exit-code
