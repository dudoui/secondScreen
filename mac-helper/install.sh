#!/bin/sh
# Installs the now-playing server as a LaunchAgent (starts at login, restarts if it dies).
# Uninstall:
#   launchctl bootout gui/$(id -u)/dev.secondscreen.nowplaying
#   rm ~/Library/LaunchAgents/dev.secondscreen.nowplaying.plist
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
PY="$(command -v python3)"
PLIST="$HOME/Library/LaunchAgents/dev.secondscreen.nowplaying.plist"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>dev.secondscreen.nowplaying</string>
  <key>ProgramArguments</key>
  <array><string>$PY</string><string>$DIR/nowplaying_server.py</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardErrorPath</key><string>/tmp/secondscreen-nowplaying.log</string>
</dict>
</plist>
PL
launchctl bootout "gui/$(id -u)/dev.secondscreen.nowplaying" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
IP="$(ipconfig getifaddr en0 || ipconfig getifaddr en1 || true)"
echo "Installed. In the phone's settings, set the Mac address to: $IP:8765"
