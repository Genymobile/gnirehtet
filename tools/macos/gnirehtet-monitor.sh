#!/bin/bash
set -u

ADB_PATH="$1"
RELAY_PID_FILE="$2"
MONITOR_PID_FILE="$3"
RELAY_LOG="$4"
CONTROLLER_PID="$5"
RELAY_PORT="31416"
CONTROLLER_TTY=""

if [ "$#" -ge 7 ] && [[ "${6:-}" =~ ^[0-9]+$ ]]; then
  RELAY_PORT="$6"
  CONTROLLER_TTY="$7"
  shift 7
elif [ "$#" -ge 6 ]; then
  CONTROLLER_TTY="$6"
  shift 6
else
  shift 5
fi
WATCHED_SERIALS=("$@")

log() {
  printf "%s %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

relay_pid() {
  [ -f "$RELAY_PID_FILE" ] || return 1
  cat "$RELAY_PID_FILE" 2>/dev/null
}

relay_listener_pids() {
  /usr/sbin/lsof -tiTCP:"$RELAY_PORT" -sTCP:LISTEN 2>/dev/null
}

relay_is_running() {
  local pid
  pid="$(relay_pid || true)"
  if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
    return 0
  fi
  [ -n "$(relay_listener_pids)" ]
}

controller_is_running() {
  [ -n "$CONTROLLER_PID" ] || return 1
  kill -0 "$CONTROLLER_PID" >/dev/null 2>&1
}

stop_relay() {
  local pid
  pid="$(relay_pid || true)"
  if [ -n "$pid" ]; then
    log "Stopping relay PID $pid after device loss."
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
  local listener_pid
  while IFS= read -r listener_pid; do
    [ -n "$listener_pid" ] || continue
    if [ "$listener_pid" != "$pid" ]; then
      log "Stopping relay listener PID $listener_pid after device loss."
      kill "$listener_pid" >/dev/null 2>&1 || true
      sleep 1
      if kill -0 "$listener_pid" >/dev/null 2>&1; then
        kill -9 "$listener_pid" >/dev/null 2>&1 || true
      fi
    fi
  done < <(relay_listener_pids)
  rm -f "$RELAY_PID_FILE"
}

quit_controller() {
  if [ -n "$CONTROLLER_PID" ] && kill -0 "$CONTROLLER_PID" >/dev/null 2>&1; then
    log "Quitting controller PID $CONTROLLER_PID after device loss."
    kill "$CONTROLLER_PID" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$CONTROLLER_PID" >/dev/null 2>&1; then
      kill -9 "$CONTROLLER_PID" >/dev/null 2>&1 || true
    fi
  fi

  if [ -n "$CONTROLLER_TTY" ]; then
    log "Closing Terminal session $CONTROLLER_TTY after device loss."
    (
      sleep 1
      /usr/bin/osascript >/dev/null 2>&1 <<EOF || true
tell application "Terminal"
  repeat with terminalWindow in windows
    repeat with terminalTab in tabs of terminalWindow
      if tty of terminalTab is "$CONTROLLER_TTY" then
        close terminalWindow
        return
      end if
    end repeat
  end repeat
end tell
EOF
    ) >/dev/null 2>&1 </dev/null &
  fi
}

connected_serials() {
  "$ADB_PATH" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }'
}

any_watched_device_connected() {
  local connected
  connected="$(connected_serials)"
  local watched
  for watched in "${WATCHED_SERIALS[@]}"; do
    if printf "%s\n" "$connected" | grep -Fxq "$watched"; then
      return 0
    fi
  done
  return 1
}

cleanup_monitor_file() {
  rm -f "$MONITOR_PID_FILE"
}
trap cleanup_monitor_file EXIT

log "Monitoring selected device(s): ${WATCHED_SERIALS[*]}"
log "Relay log: $RELAY_LOG"

missing_count=0
while controller_is_running; do
  if any_watched_device_connected; then
    missing_count=0
  else
    missing_count=$((missing_count + 1))
    log "No watched devices connected; missing check $missing_count/2."
    if [ "$missing_count" -ge 2 ]; then
      stop_relay
      quit_controller
      log "All watched devices disconnected. Monitor exiting."
      exit 0
    fi
  fi
  sleep 3
done

if relay_is_running; then
  stop_relay
fi
log "Controller is no longer running. Monitor exiting."
