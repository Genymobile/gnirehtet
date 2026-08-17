# macOS controller kit

This folder contains a small macOS controller for gnirehtet Android reverse
tethering.

It can:

- list authorized Android devices from `adb devices -l`;
- select one or more devices;
- install the Android client APK;
- start and stop reverse tethering;
- uninstall the Android app from selected devices;
- install missing `adb` and Java 17 dependencies with Homebrew;
- stop the desktop relay when all selected devices disappear from ADB.

When tethering starts, the controller maps Android's
`localabstract:gnirehtet` socket to the desktop relay TCP port. This matches the
Android client protocol used by gnirehtet.

The controller is intentionally implemented as shell scripts wrapped by a small
`.app` launcher. This keeps the tool inspectable and avoids requiring Xcode to
run it.

## Build the distributable kit

From the repository root:

```bash
tools/macos/package.sh
```

The script builds:

- `:app:assembleDebug`
- `:relay-java:assembleDebug`

Then it assembles:

```text
build/macos-kit/Gnirehtet-Android16-Mac-Kit/
```

The generated kit contains:

- `Gnirehtet Controller.app`
- `Gnirehtet Controller.command`
- `gnirehtet-monitor.sh`
- `gnirehtet-android16-debug.apk`
- `gnirehtet.jar`
- `README.txt`

Generated APK/JAR/app bundles are not checked into git.
