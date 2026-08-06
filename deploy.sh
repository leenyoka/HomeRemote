#!/bin/bash
set -e

export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export PATH="$PATH:/c/Users/Linda/AppData/Local/Android/Sdk/platform-tools"

echo "Building..."
./gradlew assembleDebug --quiet

echo "Installing..."
adb install -r "app/build/outputs/apk/debug/app-debug.apk"

echo "Restarting app..."
adb shell am force-stop com.homeremote
sleep 1
adb shell am start -n com.homeremote/.MainActivity

IP=$(adb shell ip addr show wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "Ready: http://$IP:8080/"
