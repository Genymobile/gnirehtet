## Automatically

Just execute:

    gradle start

## Semi-automatically

You can also run the server and the client separately.

Build and run the server (keep it open):

    gradle serverStart

From another terminal, build the Android app, forward the port, and start:

    gradle clientStart

To stop the client:

    gradle clientStop

## Manually

To build the project:

    gradle debug

To start the _relay_ server on the host:

    java -jar relay/build/libs/relay.jar

To start the reverse tethering:

    adb reverse tcp:1080 tcp:1080
    adb install app/build/outputs/apk/app-debug.apk
    adb shell am startservice -a com.genymobile.gnirehtet.START

To stop the client:

    adb shell am startservice -a com.genymobile.gnirehtet.STOP

## Why `gnirehtet`?

    rev <<< tethering
