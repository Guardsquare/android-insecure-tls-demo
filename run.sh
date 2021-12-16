#!/bin/bash

set -e

progress(){
    GREEN="\033[0;32m"
    NC="\033[0m" # No Color

    printf "\033[0;32m[x] $@\033[0m\n"
}

if lsof -Pi :5555 -sTCP:LISTEN -t >/dev/null; then
    echo "There is already an emulator running! Please stop it before running this demo"
    exit 1
fi

progress "Launching containers"
docker-compose -f docker-compose.yaml up -d
trap "progress \"Shutting down containers\" && docker-compose -f $(pwd)/docker-compose.yaml down" EXIT

progress "Building demo app"
cd app
./gradlew assembleDebug

progress "Waiting for emulator boot to be finished"
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'

progress "Launching app on emulator"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n "com.example.insecuretls/com.example.insecuretls.WebviewActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

progress "App launched successfully! Opening https://localhost in your web browser"
python3 -m webbrowser https://localhost

progress "Entering attacker shell. Note that exiting it will shut down the containers"
docker exec -it -w /eve_files emulator_eve /bin/bash

