## Automatically

Build and run the server (keep it open):

    gradle serverStart

From another terminal, build the Android app, forward the port, and start:

    gradle clientStart

## Manually

To build the project:

    gradle debug

To start the _relay_ server on the host:

    java -jar relay/build/libs/relay.jar

To start the reverse tethering:

    adb reverse tcp:1080 tcp:1080
    adb install app/build/outputs/apk/app-debug.apk
    adb shell am start -a com.genymobile.gnirehtet.VPN

## Why `gnirehtet`?

    rev <<< tethering
