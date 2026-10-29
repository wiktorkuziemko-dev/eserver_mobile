#!/usr/bin/env bash
# Błędy kompilacji jako adnotacje GitHub – widoczne w podsumowaniu uruchomienia bez pobierania logów.
LOG="${1:-build.log}"
[ -f "$LOG" ] || exit 0
grep -vE "^> Task" "$LOG" \
  | grep -E "^e: |error:|What went wrong|^> |^   > |FAILED|Exception|\*\* BUILD FAILED|\*\* ARCHIVE FAILED|\*\* EXPORT FAILED" \
  | head -80 > errors.txt || true
split -l 6 errors.txt part_
for f in part_*; do
  [ -f "$f" ] || continue
  msg=$(sed 's/%/%25/g' "$f" | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=Build::$msg"
done
