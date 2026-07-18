#!/usr/bin/env bash
# suki 鋤 — run the local cljc actor boundary tests.
set -euo pipefail
cd "$(dirname "$0")"
exec bb test
