#!/bin/bash

# Deploy and run the application on a connected Android device

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

echo "Building and installing the application..."
./gradlew installDebug

echo "Launching the application..."
adb shell am start -n hanabix.hudble/hanabix.hudble.MainActivity

echo "Deployment complete."
