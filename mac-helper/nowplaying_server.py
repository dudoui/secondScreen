#!/usr/bin/env python3
"""Tiny LAN server exposing what is playing on this Mac.

GET /now  -> JSON {state, title, artist, album, duration, position, artId, source}
GET /art  -> cover image bytes of the current item (404 if none)

Apple Music is read through AppleScript (never launched if closed). Anything else the system
"Now Playing" knows about (Podcasts, Safari, Spotify...) is read with `media-control`
(brew install media-control). Whatever is actually playing wins; Music wins ties.
"""
import base64
import hashlib
import json
import shutil
import os
import subprocess
import sys
import tempfile
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("NOWPLAYING_PORT", "8765"))
SEP = "␞"  # unlikely to appear in titles

NOW_SCRIPT = f'''
if application "Music" is running then
  tell application "Music"
    set pState to player state as string
    if pState is "stopped" then return "stopped"
    try
      set t to current track
      set res to pState & "{SEP}" & (name of t) & "{SEP}" & (artist of t) & "{SEP}" & (album of t)
      set res to res & "{SEP}" & (duration of t) & "{SEP}" & (player position) & "{SEP}" & (persistent ID of t)
      return res
    on error
      return pState
    end try
  end tell
else
  return "stopped"
end if
'''

ART_SCRIPT = '''
on run argv
  set outPath to item 1 of argv
  tell application "Music"
    if (count of artworks of current track) is 0 then return "none"
    set artData to raw data of artwork 1 of current track
  end tell
  set f to open for access (POSIX file outPath) with write permission
  try
    set eof f to 0
    write artData to f
  end try
  close access f
  return "ok"
end run
'''

MEDIA_CONTROL = shutil.which("media-control") or "/opt/homebrew/bin/media-control"
APP_NAMES = {
    "com.apple.podcasts": "Podcasts",
    "com.apple.Music": "Music",
    "com.apple.Safari": "Safari",
    "com.spotify.client": "Spotify",
    "com.google.Chrome": "Chrome",
    "org.mozilla.firefox": "Firefox",
}

_lock = threading.Lock()
_cache = {"at": 0.0, "now": {"state": "stopped"}}
_art = {"id": None, "bytes": None, "type": None}
_mc_art = {}  # artId -> raw artwork bytes from media-control, filled while reading /now


def osa(script, *args, timeout=5):
    r = subprocess.run(["osascript", "-e", script, *args], capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0:
        print(f"osascript failed: {r.stderr.strip()}", file=sys.stderr, flush=True)
    return r.stdout.strip()


def num(s):
    try:
        return float(s.replace(",", "."))
    except ValueError:
        return None


def music_now():
    try:
        parts = osa(NOW_SCRIPT).split(SEP)
    except Exception:
        parts = ["stopped"]
    now = {"state": parts[0] or "stopped", "source": "Music"}
    if len(parts) >= 7:
        now.update(title=parts[1], artist=parts[2], album=parts[3],
                   duration=num(parts[4]), position=num(parts[5]), artId=parts[6])
    return now


def system_now():
    """System-wide Now Playing via media-control; None if unavailable or empty."""
    try:
        r = subprocess.run([MEDIA_CONTROL, "get"], capture_output=True, text=True, timeout=5)
        d = json.loads(r.stdout or "null")
    except Exception as e:
        print(f"media-control failed: {e}", file=sys.stderr, flush=True)
        return None
    if not d or not d.get("title"):
        return None
    bundle = d.get("parentApplicationBundleIdentifier") or d.get("bundleIdentifier") or ""
    if bundle == "com.apple.Music":
        return None  # handled (better) by AppleScript
    playing = bool(d.get("playing"))
    if not playing and bundle != "com.apple.podcasts":
        return None  # a paused browser tab isn't worth showing; a paused podcast is
    elapsed = d.get("elapsedTime")
    # elapsedTime was measured at `timestamp`; bring it up to now while playing.
    if playing and elapsed is not None and d.get("timestamp"):
        try:
            at = datetime.fromisoformat(d["timestamp"].replace("Z", "+00:00"))
            elapsed += (datetime.now(timezone.utc) - at).total_seconds() * (d.get("playbackRate") or 1)
        except ValueError:
            pass
    key = f'{bundle}|{d.get("title")}|{d.get("artist")}|{d.get("album")}'
    art_id = "mc-" + hashlib.sha1(key.encode()).hexdigest()[:16]
    if d.get("artworkData") and art_id not in _mc_art:
        try:
            _mc_art.clear()
            _mc_art[art_id] = base64.b64decode(d["artworkData"])
        except ValueError:
            pass
    return {
        "state": "playing" if playing else "paused",
        "title": d.get("title"),
        "artist": d.get("artist") or None,
        "album": d.get("album") or None,
        "duration": d.get("duration"),
        "position": elapsed,
        "artId": art_id,
        "source": APP_NAMES.get(bundle, bundle.split(".")[-1].capitalize() if bundle else None),
    }


def now_playing():
    with _lock:
        if time.time() - _cache["at"] < 1.0:
            return _cache["now"]
        music = music_now()
        other = system_now() if music["state"] != "playing" else None
        if other and other["state"] == "playing":
            now = other
        elif music["state"] != "stopped":
            now = music
        else:
            now = other or {"state": "stopped"}
        _cache.update(at=time.time(), now=now)
        return now


def to_jpeg(data):
    """Android can't decode TIFF/HEIC; convert anything that isn't JPEG/PNG with sips."""
    if data[:3] == b"\xff\xd8\xff" or data[:4] == b"\x89PNG":
        return data
    fd, src = tempfile.mkstemp(suffix=".img")
    dst = src + ".jpg"
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        subprocess.run(["sips", "-s", "format", "jpeg", "-Z", "1000", src, "--out", dst],
                       capture_output=True, timeout=10)
        with open(dst, "rb") as f:
            return f.read()
    except Exception:
        return None
    finally:
        for f in (src, dst):
            if os.path.exists(f):
                os.unlink(f)


def artwork():
    now = now_playing()
    art_id = now.get("artId")
    if not art_id:
        return None, None
    with _lock:
        if _art["id"] == art_id:
            return _art["bytes"], _art["type"]
    if art_id.startswith("mc-"):
        raw = _mc_art.get(art_id)
        if raw is None:
            system_now()  # artwork may have arrived after the first read
            raw = _mc_art.get(art_id)
        data = (to_jpeg(raw) if raw else None) or b""
    else:
        fd, path = tempfile.mkstemp(suffix=".img")
        os.close(fd)
        try:
            ok = osa(ART_SCRIPT, path, timeout=10) == "ok"
            data = open(path, "rb").read() if ok else b""
        except Exception:
            data = b""
        finally:
            os.unlink(path)
    ctype = "image/png" if data[:4] == b"\x89PNG" else "image/jpeg"
    if data:  # only remember successes, so a missing cover is retried on the next request
        with _lock:
            _art.update(id=art_id, bytes=data, type=ctype)
    return data or None, ctype


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/now"):
            body = json.dumps(now_playing()).encode()
            self.reply(200, "application/json; charset=utf-8", body)
        elif self.path.startswith("/art"):
            data, ctype = artwork()
            if data:
                self.reply(200, ctype, data)
            else:
                self.reply(404, "text/plain", b"no artwork")
        else:
            self.reply(404, "text/plain", b"not found")

    def reply(self, code, ctype, body):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"Now-playing server on :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
