#!/usr/bin/env bash
set -euo pipefail

stdin="$(cat)"
session_id="$(printf '%s' "$stdin" | sed -nE 's/.*"session_id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n 1)"

if [[ -z "${session_id}" ]]; then
  exit 0
fi

track_file="/tmp/album_codex_edits_${session_id}.txt"
review_file_threshold=2

if [[ ! -f "${track_file}" ]]; then
  exit 0
fi

file_count="$(sort -u "${track_file}" | wc -l | tr -d ' ')"
rm -f "${track_file}"

if [[ ! "${file_count}" =~ ^[0-9]+$ ]]; then
  exit 0
fi

if [[ "${file_count}" -ge "${review_file_threshold}" ]]; then
  echo "[project rule] ${file_count} source/config files changed. Run the local code_review skill before finishing." >&2
  exit 2
fi

