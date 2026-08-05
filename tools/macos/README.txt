Gnirehtet Android 16 Mac Kit

What is included:
- Gnirehtet Controller.app: double-click this to open the controller menu.
- Gnirehtet Controller.command: the same controller as a direct Terminal launcher.
- gnirehtet-android16-debug.apk: Android 16-compatible gnirehtet APK.
- gnirehtet.jar: Java desktop relay used by the controller.
- logs/: created at runtime for relay and connection-loss monitor logs.

Basic use:
1. Connect one or more Android devices over USB.
2. Enable Developer Options and USB debugging on each Android device.
3. Double-click "Gnirehtet Controller.app".
4. Choose "Install/Repair dependencies with Homebrew" if ADB or Java is missing.
5. Choose Android devices. Enter ALL, a device number, or comma-separated values such as 1,2.
6. Install the bundled APK on selected devices.
7. Start tethering. Accept the Android VPN prompt if requested.

Runtime tunnel:
- The controller maps Android localabstract:gnirehtet to the Mac relay port.

Stopping:
- Use "Stop tethering on selected devices" in the controller.
- Quit also performs cleanup first.
- Use "Uninstall Android app from selected devices" to stop tethering, remove
  ADB reverse mappings, and uninstall com.genymobile.gnirehtet.
- If all selected USB devices are unplugged while tethering is running, the
  controller's background monitor stops the Mac relay and quits the controller.
- The Android app also stops its VPN service after the relay has been
  disconnected for a short grace period.

Manual APK install:
adb install -r gnirehtet-android16-debug.apk

Notes:
- Homebrew must already be installed for automatic dependency repair.
- The controller installs android-platform-tools and openjdk@17 when requested.
- The relay is started in the background and writes logs to logs/gnirehtet-relay.log.
