#!/usr/bin/env bash
# Capture a genuinely active Monster Quest battle on the CI Android emulator.
# android-emulator-runner executes every YAML `script:` line in a new shell,
# so the full loop must live in this standalone executable Bash script.
set -euo pipefail

found_battle=0
for step in $(seq 1 24); do
  # 1080x2400 @420dpi: center of the right-hand '進む' control.
  adb shell input tap 890 1975
  sleep 0.4
  adb shell uiautomator dump /sdcard/monsterquest-battle.xml >/dev/null 2>&1 || true
  if adb shell cat /sdcard/monsterquest-battle.xml 2>/dev/null | grep -q '連撃'; then
    found_battle=1
    echo "Battle reached after ${step} steps"
    break
  fi
done

if [[ "$found_battle" -ne 1 ]]; then
  echo '::error::Encounter not reached after 24 forward steps'
  exit 1
fi

adb shell screencap -p /sdcard/monsterquest-battle.png
adb pull /sdcard/monsterquest-battle.png monsterquest/build/ui-screenshot/battle.png
adb pull /sdcard/monsterquest-battle.xml monsterquest/build/ui-screenshot/battle-accessibility.xml

# Trigger one strike while the first encounter is still alive. The resulting
# snapshot verifies that floating damage and the combat log are *inside* the
# clipped upper stage, not below it or off the right edge.
adb shell input tap 535 1970
sleep 0.25
adb shell screencap -p /sdcard/monsterquest-damage-popup.png
adb pull /sdcard/monsterquest-damage-popup.png monsterquest/build/ui-screenshot/damage-popup.png

# A second viewport approximates a compact 360dp-wide phone screen. Keep
# the entire scene and both edge-anchored notification containers in view.
adb shell wm size 720x1600
adb shell wm density 320
sleep 0.6
adb shell screencap -p /sdcard/monsterquest-battle-narrow.png
adb pull /sdcard/monsterquest-battle-narrow.png monsterquest/build/ui-screenshot/battle-narrow.png
