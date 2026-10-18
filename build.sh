#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

if [ "$#" -eq 0 ]; then
    set -- assembleDebug
fi
./gradlew "$@"
