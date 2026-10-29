#!/usr/bin/env bash
# Instaluje aplikację na symulatorze iPhone'a, uruchamia ją i zapisuje zrzut ekranu.
set -e
APP="$1"; BUNDLE="$2"; OUT="$3"
mkdir -p "$OUT"
UDID=$(xcrun simctl list devices available -j | python3 -c "
import json, sys
devices = json.load(sys.stdin)['devices']
phones = [d for runtime, ds in devices.items() if 'iOS' in runtime for d in ds if d['name'].startswith('iPhone')]
print(phones[-1]['udid'])")
xcrun simctl boot "$UDID" || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryLevel 100 --cellularBars 4 || true
xcrun simctl install "$UDID" "$APP"
xcrun simctl launch "$UDID" "$BUNDLE"
sleep 25
xcrun simctl io "$UDID" screenshot "$OUT/01-start.png"
xcrun simctl spawn "$UDID" log show --last 2m --predicate 'process == "iosApp"' --style compact > "$OUT/app-log.txt" 2>/dev/null || true
xcrun simctl list devices | grep "$UDID" > "$OUT/device.txt" || true
