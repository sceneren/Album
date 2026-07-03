#!/usr/bin/env bash
set -euo pipefail

stdin="$(cat)"
file_path="$(printf '%s' "$stdin" | sed -nE 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n 1)"
session_id="$(printf '%s' "$stdin" | sed -nE 's/.*"session_id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n 1)"

if [[ -z "${session_id}" || -z "${file_path}" ]]; then
  exit 0
fi

case "${file_path}" in
  *.kt|*.kts|*.java|*.xml|*.gradle|*.properties|*.toml) ;;
  *) exit 0 ;;
esac

track_file="/tmp/album_codex_edits_${session_id}.txt"
printf '%s\n' "${file_path}" >> "${track_file}"

