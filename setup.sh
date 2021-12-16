#!/bin/bash

set -e

progress(){
    GREEN="\033[0;32m"
    NC="\033[0m" # No Color

    printf "\033[0;32m[x] $@\033[0m\n"
}

progress "Getting the Android emulator container repo"
if [ -d "android-emulator-container-scripts" ]; then
    echo "Repo already checked out";
else
    git clone https://github.com/google/android-emulator-container-scripts.git;
fi
cd android-emulator-container-scripts
git fetch --all
git checkout 0d5f55c

progress "Setting up emulator image"
. ./configure.sh
emu-docker create canary "S google_apis x86_64"

progress "Setting up web frontend"
./create_web_container.sh -p user,pass -a

progress "Done. Use the run.sh script to launch the demo"
deactivate
cd ..
