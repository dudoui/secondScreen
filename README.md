# Second Screen

Always-on Android dashboard: black & white widgets on swipeable pages — clock, calendar, Open-Meteo weather, RSS news (Le Monde by default) and what is playing on your Mac.

Requires Android 8.1 (API 27) or later. The UI is in English or French: it follows the phone's language, French when the phone is in French and English otherwise.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

The `JAVA_HOME` path above (and the `adb` path below) assume JDK 17 and the Android command-line tools installed with Homebrew; adjust them to your setup.

## Install on the phone

1. Phone: Settings → About → tap *Build number* 7× → Developer options → enable **USB debugging** (and **Stay awake** while charging).
2. Plug it in, accept the prompt, then:
   ```bash
   /opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Launch *Second Screen* and allow calendar access when asked: the calendars synced on the phone (e.g. your Google account) are read automatically.
4. Disable battery optimisation for the app (Settings → Apps → Second Screen → Battery → Unrestricted).

The dashboard stays on and shows over the lock screen.

## Settings

Hold a finger still anywhere on the dashboard for about a second. The **General** tab has (labels are given in English here):

- **Extra iCal calendars** — optional extra calendars, one iCal URL per line (`webcal://` works), on top of the phone's own calendars.
- **City** — type a city and tap **Search** before saving; the weather uses the place it finds.
- **Default RSS feed** — the news feed (RSS or Atom).
- **Mac** — address of the now-playing helper, see below.
- **Night** — start and end hour (23 h to 7 h by default) during which the screen dims to minimum brightness. Set both to the same hour to turn it off.

**Save** saves; **Cancel** discards every change.

## Now playing (Mac helper)

The now-playing widget shows the track, cover and progress of what your Mac is playing. It needs a small server on the Mac:

```bash
sh mac-helper/install.sh
```

This installs a LaunchAgent that starts at login and prints the address to enter in the app's **Mac** setting (`<ip>:8765`). Run it again after changing `mac-helper/nowplaying_server.py`.

- Apple Music works out of the box; macOS may ask once to let Python control Music.
- Other apps (Podcasts, Spotify, browsers…) need `brew install media-control`.
- The server answers anyone on the local network, without authentication, and the phone must be on the same network.
- If the Mac's IP address changes, update the setting on the phone.
- `NOWPLAYING_PORT` changes the port (edit the plist to set it); errors go to `/tmp/secondscreen-nowplaying.log`.

Uninstall:

```bash
launchctl bootout gui/$(id -u)/dev.secondscreen.nowplaying
rm ~/Library/LaunchAgents/dev.secondscreen.nowplaying.plist
```

On the dashboard, tap a cover to switch it between greyscale and colour.

## Customising pages

Open the settings → **Pages** tab. Each page is a 4×2 grid:

- **＋ Page**, *Rename*, ◀ ▶ (reorder), *Delete* manage pages; *Reset* restores the default layout.
- Tap an empty cell to add a widget there (it starts at 1×1).
- Tap a widget to select it, then drag its ↘ corner handle to stretch or shrink it; it snaps to the nearest size that fits.
- A selected widget can also be moved (tap a highlighted cell), configured (event count, news title) or deleted.

Sizes: Small 1×1, Medium 2×1, Large 2×2, Huge 4×2. Every widget adapts its content to its size.
The default layout lives in `app/src/main/java/dev/secondscreen/config/Layout.kt`.

## Languages

All the text lives in `app/src/main/java/dev/secondscreen/i18n/Strings.kt`: a `Strings` interface with one object per language (`En`, `Fr`). To add a language, add an object implementing `Strings` and return it from `stringsFor`; the build fails until every entry is translated.

Day and month names follow the language of the text. Times stay on the 24-hour clock in both languages. The default pages are named in the language in use when they are created; pages you have already saved keep their names.

## Colour

Color is used only for: red = event ongoing / within 30 min, storm, rain within the hour, news < 30 min old; amber = rain likely, and a small dot on a widget whose data is over an hour old or whose last refresh failed.
