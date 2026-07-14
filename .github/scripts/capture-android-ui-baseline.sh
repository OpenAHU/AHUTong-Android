#!/bin/sh
set -eu

output_dir="${RUNNER_TEMP:?}/android-ui-baseline"
window_xml="${RUNNER_TEMP:?}/window.xml"
mkdir -p "$output_dir"

# Always preserve process diagnostics with the partial screenshots. This keeps
# emulator-only navigation or rendering crashes debuggable from the artifact
# instead of requiring access to the ephemeral runner.
trap 'adb logcat -d > "$output_dir/logcat.txt" 2>&1 || true' EXIT

node_point() {
  attribute="$1"
  value="$2"
  path="$3"
  python3 - "$attribute" "$value" "$path" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

attribute, value, path = sys.argv[1], sys.argv[2], sys.argv[3]
for node in ET.parse(path).iter("node"):
    if node.attrib.get(attribute) == value:
        values = list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        print((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
        break
PY
}

node_last_point() {
  attribute="$1"
  value="$2"
  path="$3"
  python3 - "$attribute" "$value" "$path" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

attribute, value, path = sys.argv[1], sys.argv[2], sys.argv[3]
matches = []
for node in ET.parse(path).iter("node"):
    if node.attrib.get(attribute) == value:
        values = list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        matches.append(((values[0] + values[2]) // 2, (values[1] + values[3]) // 2))
if matches:
    print(*matches[-1])
PY
}

node_contains_point() {
  attribute="$1"
  value="$2"
  path="$3"
  python3 - "$attribute" "$value" "$path" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

attribute, value, path = sys.argv[1], sys.argv[2], sys.argv[3]
for node in ET.parse(path).iter("node"):
    if value in node.attrib.get(attribute, ""):
        values = list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        print((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
        break
PY
}

tap_text() {
  label="$1"
  attempt=0
  while [ "$attempt" -lt 10 ]; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "$window_xml" >/dev/null
    point="$(node_point text "$label" "$window_xml")"
    if [ -z "$point" ]; then
      point="$(node_point content-desc "$label" "$window_xml")"
    fi
    if [ -n "$point" ]; then
      # The point is deliberately split into x/y arguments for adb input.
      # shellcheck disable=SC2086
      adb shell input tap $point
      sleep 2
      return 0
    fi
    anr_wait_point="$(node_point resource-id android:id/aerr_wait "$window_xml")"
    if [ -n "$anr_wait_point" ]; then
      # shellcheck disable=SC2086
      adb shell input tap $anr_wait_point
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  cp "$window_xml" "$output_dir/missing-${label}.xml"
  echo "Timed out tapping Android node: $label" >&2
  return 1
}

tap_last_text() {
  label="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$window_xml" >/dev/null
  point="$(node_last_point text "$label" "$window_xml")"
  if [ -z "$point" ]; then
    point="$(node_last_point content-desc "$label" "$window_xml")"
  fi
  if [ -z "$point" ]; then
    echo "Could not find Android node: $label" >&2
    return 1
  fi
  # shellcheck disable=SC2086
  adb shell input tap $point
  sleep 2
}

tap_text_contains() {
  label="$1"
  attempt=0
  while [ "$attempt" -lt 10 ]; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "$window_xml" >/dev/null
    point="$(node_contains_point text "$label" "$window_xml")"
    if [ -z "$point" ]; then
      point="$(node_contains_point content-desc "$label" "$window_xml")"
    fi
    if [ -n "$point" ]; then
      # shellcheck disable=SC2086
      adb shell input tap $point
      sleep 2
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  cp "$window_xml" "$output_dir/missing-contains-${label}.xml"
  echo "Timed out tapping Android node containing: $label" >&2
  return 1
}

tap_visible_text() {
  label="$1"
  attempt=0
  while [ "$attempt" -lt 10 ]; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "$window_xml" >/dev/null
    point="$(node_point text "$label" "$window_xml")"
    if [ -z "$point" ]; then
      point="$(node_point content-desc "$label" "$window_xml")"
    fi
    if [ -n "$point" ]; then
      # Compose keeps off-screen scroll content in the semantics tree. Tapping
      # its raw y coordinate can land on the system Home gesture area, so move
      # the viewport until the node is genuinely inside the app window.
      # shellcheck disable=SC2086
      set -- $point
      if [ "$2" -ge 180 ] && [ "$2" -le 2300 ]; then
        adb shell input tap "$1" "$2"
        sleep 2
        return 0
      fi
      if [ "$2" -gt 2300 ]; then
        adb shell input swipe 585 2100 585 650 450
      else
        adb shell input swipe 585 650 585 2100 450
      fi
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  cp "$window_xml" "$output_dir/missing-visible-${label}.xml"
  echo "Timed out tapping visible Android node: $label" >&2
  return 1
}

wait_for_text() {
  label="$1"
  attempt=0
  while [ "$attempt" -lt 10 ]; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "$window_xml" >/dev/null
    if grep -F "text=\"$label\"" "$window_xml" >/dev/null || \
       grep -F "content-desc=\"$label\"" "$window_xml" >/dev/null; then
      return 0
    fi
    # GitHub's emulator launcher can occasionally show a transient Quickstep
    # ANR over the app. Choose "Wait" and continue polling for the app node so
    # the captured baseline never records the system dialog as product UI.
    anr_wait_point="$(node_point resource-id android:id/aerr_wait "$window_xml")"
    if [ -n "$anr_wait_point" ]; then
      # shellcheck disable=SC2086
      adb shell input tap $anr_wait_point
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  cp "$window_xml" "$output_dir/missing-${label}.xml"
  echo "Timed out waiting for Android node: $label" >&2
  return 1
}

capture() {
  name="$1"
  adb exec-out screencap -p > "$output_dir/$name.png"
  adb shell uiautomator dump "/sdcard/$name.xml" >/dev/null || true
  adb pull "/sdcard/$name.xml" "$output_dir/$name.xml" >/dev/null || true
}

adb shell wm size 1170x2532
adb shell wm density 480
adb shell settings put global hide_error_dialogs 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.ahu.ahutong
adb shell am start -W -n com.ahu.ahutong/.MainActivity
sleep 5

wait_for_text 同意
capture 01-first-dialog
tap_text 同意
capture 02-second-dialog
tap_text 同意
capture 03-third-dialog
tap_text 同意
sleep 3
capture 04-login
tap_last_text 登录
capture 04-error-login

adb shell am force-stop com.ahu.ahutong
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.ahu.ahutong.BaselineStateSeederTest#seedDeterministicLoggedInMockState \
  com.ahu.ahutong.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -W -n com.ahu.ahutong/.MainActivity
sleep 8

capture 05-home
tap_text 课表
capture 06-schedule
tap_text 计算机网络
capture 07-course-detail
adb shell input keyevent 4
tap_text 小工具
capture 07-tools
tap_text 设置
capture 08-settings
tap_text 小工具

tap_text 电话本
capture 09-phone-book
adb shell input keyevent 4
tap_text 校历
sleep 4
capture 10-school-calendar
adb shell input keyevent 4
adb shell pm grant com.ahu.ahutong android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.ahu.ahutong android.permission.ACCESS_FINE_LOCATION
tap_text 天气
sleep 4
capture 11-weather
adb shell input keyevent 4
tap_text 学习资料
sleep 4
capture 12-study-repository
adb shell input keyevent 4
tap_text 成绩单
sleep 4
capture 13-grades
adb shell input keyevent 4
tap_text 考场查询
sleep 4
capture 14-exams
adb shell input keyevent 4
tap_text 设置
tap_text 偏好设置
capture 15-preferences
adb shell pm grant com.ahu.ahutong android.permission.POST_NOTIFICATIONS || true
tap_text 课前提醒
sleep 2
capture 15-course-reminder-enabled
adb shell input keyevent 4
tap_text 小工具
adb shell input swipe 580 1900 580 700 500
sleep 2
capture 15-widget-preview
adb shell input swipe 580 700 580 1900 500
tap_text 空闲教室
# Campus selection is immediate, while the debug building fixture is loaded by
# a coroutine. Wait for its first chip so the query cannot race the load and
# incorrectly report an empty result on slower GitHub emulators.
wait_for_text "博学南楼"
tap_visible_text "开始查询空闲教室"
wait_for_text "201 教室"
# The helper may need to scroll the query button into view. Return to the top of
# the collapsed result layout before capturing the Android reference viewport.
adb shell input swipe 540 500 540 1900 600
adb shell input swipe 540 500 540 1900 600
sleep 1
capture 15-free-classroom
adb shell input keyevent 4
tap_text 失物招领
wait_for_text "文典阁三楼捡到 U 盘 - 请描述外观后领取"
capture 15-lost-found
tap_text "文典阁三楼捡到 U 盘 - 请描述外观后领取"
capture 15-lost-found-detail
adb shell input keyevent 4
tap_text +
wait_for_text 发布帖子
capture 15-lost-found-publish
# A second system Back can be consumed by the Compose dialog/navigation
# transition. Relaunching deterministically restores the persisted home route
# and keeps this evidence step independent from transient back-stack timing.
adb shell am force-stop com.ahu.ahutong
adb shell am start -W -n com.ahu.ahutong/.MainActivity
wait_for_text 校园卡余额
capture 15-home-return
tap_text_contains "充"
wait_for_text "校园卡充值"
capture 15-card-recharge
tap_text "请输入金额"
adb shell input text 10
adb shell input keyevent 66
tap_text "确认"
wait_for_text "确认支付"
capture 15-card-recharge-dialog
tap_text "取消"
adb shell input keyevent 4
wait_for_text "浴室缴费"
tap_text "浴室缴费"
wait_for_text "选择浴室"
capture 15-bathroom-payment
adb shell input keyevent 4
wait_for_text "电控缴费"
tap_text "电控缴费"
wait_for_text "选择校区"
capture 15-electricity-payment
adb shell input keyevent 4
tap_text "设置"
# The product exposes Debug by tapping the application card eight times within
# one second. Fixed coordinates target that card in the 1170x2532 evidence
# viewport; there is intentionally no product-only test route.
for _ in 1 2 3 4 5 6 7 8; do
  adb shell input tap 600 680
done
wait_for_text "Debug"
capture 15-debug
adb shell input keyevent 4
tap_text "主页"
wait_for_text "校园卡余额"
tap_text 校园卡余额
wait_for_text "QR Code"
capture 16-card-qrcode

seed_state() {
  method="$1"
  adb shell am force-stop com.ahu.ahutong
  adb shell am instrument -w \
    -e class "com.ahu.ahutong.BaselineStateSeederTest#$method" \
    com.ahu.ahutong.test/androidx.test.runner.AndroidJUnitRunner
  adb shell am start -W -n com.ahu.ahutong/.MainActivity
}

seed_state seedSlowLoadingMockState
sleep 1
capture 16-loading-home
tap_text 课表
capture 17-loading-schedule
tap_text 小工具
tap_text 成绩单
capture 18-loading-grades
adb shell input keyevent 4
tap_text 考场查询
capture 19-loading-exams
adb shell input keyevent 4
tap_text 空闲教室
wait_for_text "开始查询空闲教室"
tap_visible_text "开始查询空闲教室"
capture 19-loading-free-classroom
adb shell input keyevent 4
tap_text 失物招领
capture 19-loading-lost-found

seed_state seedEmptyMockState
sleep 4
capture 18-empty-home
tap_text 课表
capture 19-empty-schedule
tap_text 小工具
tap_text 成绩单
capture 20-empty-grades
adb shell input keyevent 4
tap_text 考场查询
capture 21-empty-exams
adb shell input keyevent 4
tap_text 空闲教室
wait_for_text "开始查询空闲教室"
tap_visible_text "开始查询空闲教室"
capture 21-empty-free-classroom
adb shell input keyevent 4
tap_text 失物招领
capture 21-empty-lost-found

seed_state seedNetworkErrorMockState
sleep 4
capture 22-error-home
tap_text 课表
sleep 2
capture 23-error-schedule
tap_text 小工具
tap_text 成绩单
capture 24-error-grades
adb shell input keyevent 4
tap_text 考场查询
capture 25-error-exams
adb shell input keyevent 4
tap_text 空闲教室
wait_for_text "开始查询空闲教室"
tap_visible_text "开始查询空闲教室"
capture 25-error-free-classroom
adb shell input keyevent 4
tap_text 失物招领
capture 25-error-lost-found
