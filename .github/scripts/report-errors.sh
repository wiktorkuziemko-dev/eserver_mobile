#!/usr/bin/env bash
# Błędy kompilacji jako adnotacje GitHub – widoczne w podsumowaniu uruchomienia bez pobierania logów.
LOG="${1:-build.log}"
[ -f "$LOG" ] || exit 0
{
  # Błędy kompilatora Kotlin / Swift / linkera
  grep -E "e: file|e: /|error:|warning: unable|ld: |Undefined symbol" "$LOG" | grep -v "Compilation finished with errors" | head -60
  # Opis błędu Gradle wraz z kontekstem (np. lista problemów z metadanymi AAR)
  grep -A 40 "What went wrong" "$LOG" | grep -vE "^\* Try|Run with|Get more help|^$" | head -60
} | awk '!seen[$0]++' | head -100 > errors.txt || true
split -l 8 errors.txt part_
for f in part_*; do
  [ -f "$f" ] || continue
  msg=$(sed 's/%/%25/g' "$f" | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=Build::$msg"
done
