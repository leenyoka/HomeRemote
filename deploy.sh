#!/bin/bash
set -e

export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export PATH="$PATH:/c/Users/Linda/AppData/Local/Android/Sdk/platform-tools"
TV="YOUR_TV_IP:5555"
adb connect "$TV" >/dev/null 2>&1

echo "Building..."
./gradlew assembleDebug --quiet

echo "Installing..."
adb -s "$TV" install -r "app/build/outputs/apk/debug/app-debug.apk"

echo "Restarting app..."
adb -s "$TV" shell am force-stop com.homeremote
sleep 1
adb -s "$TV" shell am start -n com.homeremote/.MainActivity
sleep 2

echo "Re-enabling accessibility..."
adb -s "$TV" shell settings put secure enabled_accessibility_services com.homeremote/.RemoteAccessibilityService
adb -s "$TV" shell settings put secure accessibility_enabled 1

echo "Waiting for server..."
for i in 1 2 3 4 5 6 7 8 9 10; do
  result=$(adb -s "$TV" shell "printf 'GET /api/ping HTTP/1.0\r\n\r\n' | nc 127.0.0.1 8080 2>&1" 2>/dev/null)
  if echo "$result" | grep -q "200"; then
    break
  fi
  sleep 1
done

IP=$(adb -s "$TV" shell ip addr show wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "Ready: http://$IP:8080/"
