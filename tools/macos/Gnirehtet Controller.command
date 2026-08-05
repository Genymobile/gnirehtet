#!/bin/bash
set -u

APP_NAME="Gnirehtet Controller"
RELAY_PORT="31416"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$SCRIPT_DIR/gnirehtet-android16-debug.apk"
JAR_PATH="$SCRIPT_DIR/gnirehtet.jar"
LOG_DIR="$SCRIPT_DIR/logs"
RELAY_LOG="$LOG_DIR/gnirehtet-relay.log"
RELAY_PID_FILE="$LOG_DIR/gnirehtet-relay.pid"
MONITOR_LOG="$LOG_DIR/gnirehtet-monitor.log"
MONITOR_PID_FILE="$LOG_DIR/gnirehtet-monitor.pid"
MONITOR_SCRIPT="$SCRIPT_DIR/gnirehtet-monitor.sh"

SELECTED_SERIALS=()
SELECTED_LABELS=()

pause() {
  printf "\nPress Enter to continue..."
  read -r _
}

header() {
  clear
  printf "%s\n" "$APP_NAME"
  printf "%s\n" "Android 16 reverse tethering over USB"
  printf "\n"
  if [ "${#SELECTED_LABELS[@]}" -eq 0 ]; then
    printf "Selected devices: none\n"
  else
    printf "Selected devices:\n"
    local label
    for label in "${SELECTED_LABELS[@]}"; do
      printf "  - %s\n" "$label"
    done
  fi
  printf "\n"
}

find_executable() {
  local candidate
  for candidate in "$@"; do
    if [ -x "$candidate" ]; then
      printf "%s" "$candidate"
      return 0
    fi
  done
  return 1
}

adb_path() {
  find_executable \
    "/opt/homebrew/bin/adb" \
    "/usr/local/bin/adb" \
    "$HOME/Documents/platform-tools/adb" \
    "$(command -v adb 2>/dev/null || true)"
}

java_path() {
  find_executable \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java" \
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java" \
    "/usr/bin/java" \
    "$(command -v java 2>/dev/null || true)"
}

brew_path() {
  find_executable "/opt/homebrew/bin/brew" "/usr/local/bin/brew" "$(command -v brew 2>/dev/null || true)"
}

install_dependencies() {
  header
  local brew
  brew="$(brew_path || true)"
  if [ -z "$brew" ]; then
    printf "Homebrew is missing.\n"
    printf "Install Homebrew from https://brew.sh, then run this controller again.\n"
    pause
    return
  fi
  printf "This will run:\n  brew install android-platform-tools openjdk@17\n\n"
  printf "Continue? [y/N] "
  read -r answer
  case "$answer" in
    y|Y|yes|YES)
      "$brew" install android-platform-tools openjdk@17
      ;;
    *)
      printf "Cancelled.\n"
      ;;
  esac
  pause
}

require_adb() {
  local adb
  adb="$(adb_path || true)"
  if [ -n "$adb" ]; then
    printf "%s" "$adb"
    return 0
  fi
  printf "ADB is missing. Use option 2 to install/repair dependencies.\n" >&2
  return 1
}

require_java() {
  local java
  java="$(java_path || true)"
  if [ -n "$java" ]; then
    printf "%s" "$java"
    return 0
  fi
  printf "Java is missing. Use option 2 to install/repair dependencies.\n" >&2
  return 1
}

load_devices() {
  local adb="$1"
  DEVICE_SERIALS=()
  DEVICE_LABELS=()
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    case "$line" in
      "List of devices"*) continue ;;
    esac
    set -- $line
    local serial="$1"
    local state="$2"
    [ "$state" = "device" ] || continue
    local model="$serial"
    local token
    for token in $line; do
      case "$token" in
        model:*) model="${token#model:}" ;;
      esac
    done
    DEVICE_SERIALS+=("$serial")
    DEVICE_LABELS+=("$model ($serial)")
  done < <("$adb" devices -l)
}

choose_devices() {
  header
  local adb
  adb="$(require_adb)" || { pause; return; }
  load_devices "$adb"
  if [ "${#DEVICE_SERIALS[@]}" -eq 0 ]; then
    printf "No authorized Android devices found.\n"
    printf "Connect USB, enable USB debugging, and approve the trust prompt on Android.\n"
    pause
    return
  fi
  printf "Detected devices:\n"
  local i
  for ((i = 0; i < ${#DEVICE_SERIALS[@]}; i++)); do
    printf "  %d) %s\n" "$((i + 1))" "${DEVICE_LABELS[$i]}"
  done
  printf "\nEnter ALL, one number, multiple numbers, one serial, or multiple serials.\n"
  printf "Examples: ALL   1   1,2   %s\n" "${DEVICE_SERIALS[0]}"
  printf "Selection: "
  read -r selection

  SELECTED_SERIALS=()
  SELECTED_LABELS=()
  if [[ "$selection" =~ ^[Aa][Ll][Ll]$ ]]; then
    SELECTED_SERIALS=("${DEVICE_SERIALS[@]}")
    SELECTED_LABELS=("${DEVICE_LABELS[@]}")
  else
    IFS=',' read -ra picks <<< "$selection"
    local pick
    for pick in "${picks[@]}"; do
      pick="$(printf "%s" "$pick" | tr -d '[:space:]')"
      for ((i = 0; i < ${#DEVICE_SERIALS[@]}; i++)); do
        if [ "$pick" = "$((i + 1))" ] || [ "$pick" = "${DEVICE_SERIALS[$i]}" ]; then
          SELECTED_SERIALS+=("${DEVICE_SERIALS[$i]}")
          SELECTED_LABELS+=("${DEVICE_LABELS[$i]}")
        fi
      done
    done
  fi

  if [ "${#SELECTED_SERIALS[@]}" -eq 0 ]; then
    printf "\nNo matching devices selected.\n"
  else
    printf "\nSelected %d device(s).\n" "${#SELECTED_SERIALS[@]}"
  fi
  pause
}

ensure_selection() {
  if [ "${#SELECTED_SERIALS[@]}" -gt 0 ]; then
    return 0
  fi
  choose_devices
  [ "${#SELECTED_SERIALS[@]}" -gt 0 ]
}

install_apk() {
  header
  ensure_selection || return
  local adb
  adb="$(require_adb)" || { pause; return; }
  if [ ! -f "$APK_PATH" ]; then
    printf "Missing APK: %s\n" "$APK_PATH"
    pause
    return
  fi
  local serial
  for serial in "${SELECTED_SERIALS[@]}"; do
    printf "\nInstalling APK on %s...\n" "$serial"
    "$adb" -s "$serial" install -r "$APK_PATH" || { pause; return; }
  done
  printf "\nAPK installed on selected device(s).\n"
  pause
}

uninstall_apk() {
  header
  ensure_selection || return
  local adb
  adb="$(require_adb)" || { pause; return; }
  printf "This will stop tethering and uninstall com.genymobile.gnirehtet from selected device(s).\n"
  printf "Continue? [y/N] "
  read -r answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) printf "Cancelled.\n"; pause; return ;;
  esac
  local serial
  for serial in "${SELECTED_SERIALS[@]}"; do
    printf "\nStopping and uninstalling gnirehtet on %s...\n" "$serial"
    "$adb" -s "$serial" shell am start -n com.genymobile.gnirehtet/.GnirehtetActivity -a com.genymobile.gnirehtet.STOP >/dev/null 2>&1 || true
    "$adb" -s "$serial" shell am force-stop com.genymobile.gnirehtet >/dev/null 2>&1 || true
    "$adb" -s "$serial" reverse --remove-all >/dev/null 2>&1 || true
    "$adb" -s "$serial" uninstall com.genymobile.gnirehtet || true
  done
  stop_monitor
  stop_relay
  printf "\nUninstall commands complete.\n"
  pause
}

relay_is_listening() {
  /usr/sbin/lsof -nP -iTCP:"$RELAY_PORT" -sTCP:LISTEN >/dev/null 2>&1
}

relay_pid_is_running() {
  [ -f "$RELAY_PID_FILE" ] || return 1
  local pid
  pid="$(cat "$RELAY_PID_FILE" 2>/dev/null || true)"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" >/dev/null 2>&1
}

monitor_pid_is_running() {
  [ -f "$MONITOR_PID_FILE" ] || return 1
  local pid
  pid="$(cat "$MONITOR_PID_FILE" 2>/dev/null || true)"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" >/dev/null 2>&1
}

start_relay() {
  if relay_pid_is_running && relay_is_listening; then
    printf "Relay is already running as PID %s.\n" "$(cat "$RELAY_PID_FILE")"
    return 0
  fi
  if relay_is_listening; then
    printf "Relay is already listening on port %s.\n" "$RELAY_PORT"
    return 0
  fi
  local java
  java="$(require_java)" || return 1
  if [ ! -f "$JAR_PATH" ]; then
    printf "Missing relay jar: %s\n" "$JAR_PATH" >&2
    return 1
  fi
  mkdir -p "$LOG_DIR"
  : > "$RELAY_LOG"
  nohup "$java" -jar "$JAR_PATH" relay >>"$RELAY_LOG" 2>&1 &
  local relay_pid="$!"
  printf "%s\n" "$relay_pid" > "$RELAY_PID_FILE"
  sleep 1
  if relay_pid_is_running && relay_is_listening; then
    printf "Relay started as PID %s.\n" "$relay_pid"
    printf "Relay log: %s\n" "$RELAY_LOG"
    return 0
  fi
  printf "Relay failed to start. Last log lines:\n" >&2
  tail -40 "$RELAY_LOG" >&2 || true
  return 1
}

start_monitor() {
  local adb="$1"
  shift
  if [ "$#" -eq 0 ] || [ ! -x "$MONITOR_SCRIPT" ]; then
    return 0
  fi
  stop_monitor
  mkdir -p "$LOG_DIR"
  : > "$MONITOR_LOG"
  nohup "$MONITOR_SCRIPT" "$adb" "$RELAY_PID_FILE" "$MONITOR_PID_FILE" "$RELAY_LOG" "$$" "$@" >>"$MONITOR_LOG" 2>&1 &
  local monitor_pid="$!"
  printf "%s\n" "$monitor_pid" > "$MONITOR_PID_FILE"
  printf "Connection-loss monitor started as PID %s.\n" "$monitor_pid"
}

stop_monitor() {
  if monitor_pid_is_running; then
    local monitor_pid
    monitor_pid="$(cat "$MONITOR_PID_FILE")"
    kill "$monitor_pid" >/dev/null 2>&1 || true
  fi
  rm -f "$MONITOR_PID_FILE"
}

start_tethering() {
  header
  ensure_selection || return
  local adb
  adb="$(require_adb)" || { pause; return; }
  printf "Starting relay...\n"
  start_relay || {
    printf "Relay did not start. Use option 2 to install/repair dependencies.\n"
    pause
    return
  }
  local serial
  for serial in "${SELECTED_SERIALS[@]}"; do
    printf "\nStarting gnirehtet on %s...\n" "$serial"
    "$adb" -s "$serial" reverse tcp:"$RELAY_PORT" tcp:"$RELAY_PORT" || { pause; return; }
    "$adb" -s "$serial" shell am start -n com.genymobile.gnirehtet/.GnirehtetActivity -a com.genymobile.gnirehtet.START || { pause; return; }
  done
  start_monitor "$adb" "${SELECTED_SERIALS[@]}"
  printf "\nStart commands sent. Accept the Android VPN prompt if requested.\n"
  pause
}

stop_tethering() {
  header
  local adb
  adb="$(require_adb)" || { pause; return; }
  local targets=()
  if [ "${#SELECTED_SERIALS[@]}" -gt 0 ]; then
    targets=("${SELECTED_SERIALS[@]}")
  else
    load_devices "$adb"
    targets=("${DEVICE_SERIALS[@]}")
  fi
  if [ "${#targets[@]}" -eq 0 ]; then
    printf "No authorized Android devices found. Stopping relay only.\n"
  fi
  local serial
  for serial in "${targets[@]}"; do
    printf "\nStopping gnirehtet on %s...\n" "$serial"
    "$adb" -s "$serial" shell am start -n com.genymobile.gnirehtet/.GnirehtetActivity -a com.genymobile.gnirehtet.STOP || true
    "$adb" -s "$serial" shell am force-stop com.genymobile.gnirehtet || true
    "$adb" -s "$serial" reverse --remove-all || true
  done
  stop_monitor
  stop_relay
  printf "\nStop commands sent and relay stopped.\n"
  pause
}

stop_relay() {
  if relay_pid_is_running; then
    local relay_pid
    relay_pid="$(cat "$RELAY_PID_FILE")"
    kill "$relay_pid" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$relay_pid" >/dev/null 2>&1; then
      kill -9 "$relay_pid" >/dev/null 2>&1 || true
    fi
  fi
  rm -f "$RELAY_PID_FILE"
}

cleanup_and_quit() {
  header
  printf "Stopping any running gnirehtet sessions before quitting...\n"
  local adb
  adb="$(adb_path || true)"
  if [ -n "$adb" ]; then
    load_devices "$adb"
    local serial
    for serial in "${DEVICE_SERIALS[@]}"; do
      printf "Stopping gnirehtet on %s...\n" "$serial"
      "$adb" -s "$serial" shell am start -n com.genymobile.gnirehtet/.GnirehtetActivity -a com.genymobile.gnirehtet.STOP >/dev/null 2>&1 || true
      "$adb" -s "$serial" shell am force-stop com.genymobile.gnirehtet >/dev/null 2>&1 || true
      "$adb" -s "$serial" reverse --remove-all >/dev/null 2>&1 || true
    done
  else
    printf "ADB not found; stopping relay only.\n"
  fi
  stop_monitor
  stop_relay
  printf "Cleanup complete.\n"
  sleep 1
  exit 0
}

show_status() {
  header
  local adb
  adb="$(require_adb)" || { pause; return; }
  printf "ADB devices:\n"
  "$adb" devices -l
  printf "\nRelay listener:\n"
  /usr/sbin/lsof -nP -iTCP:"$RELAY_PORT" -sTCP:LISTEN || printf "Relay is not listening.\n"
  printf "\nRelay PID file:\n"
  if relay_pid_is_running; then
    printf "Relay PID %s is running.\n" "$(cat "$RELAY_PID_FILE")"
  else
    printf "No running relay PID recorded.\n"
  fi
  printf "\nMonitor PID file:\n"
  if monitor_pid_is_running; then
    printf "Monitor PID %s is running.\n" "$(cat "$MONITOR_PID_FILE")"
  else
    printf "No running monitor PID recorded.\n"
  fi
  printf "\nRelay log:\n%s\n" "$RELAY_LOG"
  if [ -f "$RELAY_LOG" ]; then
    tail -20 "$RELAY_LOG"
  else
    printf "No relay log yet.\n"
  fi
  printf "\nMonitor log:\n%s\n" "$MONITOR_LOG"
  if [ -f "$MONITOR_LOG" ]; then
    tail -20 "$MONITOR_LOG"
  else
    printf "No monitor log yet.\n"
  fi
  pause
}

main_menu() {
  while true; do
    header
    printf "1) Choose Android devices\n"
    printf "2) Install/Repair dependencies with Homebrew\n"
    printf "3) Install bundled APK on selected devices\n"
    printf "4) Uninstall Android app from selected devices\n"
    printf "5) Start tethering on selected devices\n"
    printf "6) Stop tethering on selected devices\n"
    printf "7) Status\n"
    printf "8) Quit\n"
    printf "\nChoice: "
    read -r choice
    case "$choice" in
      1) choose_devices ;;
      2) install_dependencies ;;
      3) install_apk ;;
      4) uninstall_apk ;;
      5) start_tethering ;;
      6) stop_tethering ;;
      7) show_status ;;
      8|q|Q) cleanup_and_quit ;;
      *) printf "Unknown choice.\n"; pause ;;
    esac
  done
}

main_menu
