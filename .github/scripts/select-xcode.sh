#!/usr/bin/env bash
# Najnowszy stabilny Xcode dostępny na maszynie (App Store wymaga aktualnego SDK).
set -e
XCODE=$(ls -d /Applications/Xcode_*.app 2>/dev/null | grep -vi beta | sort -V | tail -1)
if [ -n "$XCODE" ]; then sudo xcode-select -s "$XCODE"; fi
xcodebuild -version
