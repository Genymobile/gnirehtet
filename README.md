# Gnirehtet

This project provides **reverse tethering** over `adb` for Android: it
allows devices to use the internet connection of the computer they are plugged
on. It does not require any _root_ access (neither on the device nor on the
computer). It works on _GNU/Linux_, _Windows_ and _Mac OS_.

Currently, it relays [TCP] and [UDP] over [IPv4] traffic, but it does not
support [IPv6] (yet?).

[TCP]: https://en.wikipedia.org/wiki/Transmission_Control_Protocol
[UDP]: https://fr.wikipedia.org/wiki/User_Datagram_Protocol
[IPv4]: https://en.wikipedia.org/wiki/IPv4
[IPv6]: https://en.wikipedia.org/wiki/IPv6


However, since the `gnirehtet` script is written in [Bash], it's a bit more
complicated on _Windows_. If you are using _Windows_, then you have several
choices:
 - execute some commands [manually](#manually);
 - run _bash_ (using [cygwin] or [gitbash]);
 - contribute a new script `gnirehtet.bat` for _Windows_.

[bash]: https://en.wikipedia.org/wiki/Bash_%28Unix_shell%29
[gitbash]: https://git-for-windows.github.io/
[cygwin]: https://www.cygwin.com/


## Requirements

The Android application requires at least API 21 (Android 5.0).

_Java 8_ is required on your computer. On Debian-based distros, install the
package `openjdk-8-jre`.

You need a recent version of [adb] (where `adb reverse` is implemented, it
works with 1.0.36). On Debian-based distros, check for `android-tools-adb` or
`adb`.

Make sure you [enabled adb debugging][enable-adb] on your device(s).

[adb]: https://developer.android.com/studio/command-line/adb.html
[enable-adb]: https://developer.android.com/studio/command-line/adb.html#Enabling


## Download

Download the latest stable [release].

[release]: https://github.com/Genymobile/gnirehtet/releases

Then extract it. You get three files:
 - `gnirehtet`
 - `gnirehtet.apk`
 - `relay.jar`


## Run (simple)

The application has no UI, and is intended to be controlled from the computer
only.

If you want to activate reverse tethering for exactly one device, just execute:

    ./gnirehtet rt

Reverse tethering remains active until you press _Ctrl+C_.

The very first start should open a popup to request permission:

![request](assets/request.jpg)

A "key" logo appears in the status bar whenever _Gnirehtet_ is active:

![key](assets/key.png)

## Run

You can execute the actions separately (it may be useful if you want to reverse
tether several devices simultaneously).

Start the relay server and keep it open:

    ./gnirehtet relay

Install the `apk` on your Android device:

    ./gnirehtet install [serial]

In another terminal, for each client, execute:

    ./gnirehtet start [serial]

To stop a client:

    ./gnirehtet stop [serial]

The _serial_ parameter is required only if `adb devices` outputs more than one
device.

For advanced options, call `./gnirehtet` without arguments to get more details.


## Manually

The `gnirehtet` script just exposes an interface for calling simple commands.
You can call them manually (especially if you use _Windows_, in that case,
replace `adb` by `adb.exe`).

To start the relay server:

    java -jar relay.jar

To install the apk:

    adb install -r gnirehtet.apk

To start a client:

    adb reverse tcp:31416 tcp:31416
    adb shell am startservice -a com.genymobile.gnirehtet.START

To stop a client:

    adb shell am startservice -a com.genymobile.gnirehtet.STOP


## Why _gnirehtet_?

    rev <<< tethering

(in _Bash_)


## Developers

Read the [developers page].

[developers page]: DEVELOP.md
