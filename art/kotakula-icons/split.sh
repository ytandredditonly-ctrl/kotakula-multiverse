#!/bin/bash
# Splits an icon bundle into SVG files: every "=== Folder/Name.svg" line starts a new file.
# Usage: bash art/kotakula-icons/split.sh bundle.txt
cd "$(dirname "$0")"
awk '
  /^=== / { if (out) close(out); out = substr($0, 5); dir = out; sub(/\/[^\/]*$/, "", dir); system("mkdir -p \"" dir "\""); printf "" > out; next }
  out { print >> out }
' "$1"
