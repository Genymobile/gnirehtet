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

Download the [latest release][latest]:

[`gnirehtet-v1.1.1.zip`][direct]
(SHA-256: _5bff4dbd11abd5b87211d25c36d715166b341f230f1f7614bbd1b570660980e1_)

[latest]: https://github.com/Genymobile/gnirehtet/releases/latest
[direct]: https://github.com/Genymobile/gnirehtet/releases/download/v1.1.1/gnirehtet-v1.1.1.zip


Then extract it. You get three files:
 - `gnirehtet`
 - `gnirehtet.apk`
 - `gnirehtet.jar`


## Run (simple)

_Note: On Windows, replace `./gnirehtet` by `gnirehtet` in the following
commands._

The application has no UI, and is intended to be controlled from the computer
only.

If you want to activate reverse tethering for exactly one device, just execute:

    ./gnirehtet run

Reverse tethering remains active until you press _Ctrl+C_.

The very first start should open a popup to request permission:

![request](assets/request.jpg)

A "key" logo appears in the status bar whenever _Gnirehtet_ is active:

![key](assets/key.png)

If an older version of _gnirehtet_ was already installed on your device, you
have to install the new one first:

    ./gnirehtet install


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


## Run manually

The `gnirehtet` program exposes a simple command-line interface that executes
lower-level commands. You can call them manually instead.

To start the relay server:

    java -jar gnirehtet.jar relay

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


## Licence

    Copyright (C) 2017 Genymobile

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


## Our blog posts introducing gnirehtet

- <https://medium.com/@rom1v/gnirehtet-reverse-tethering-android-2afacdbdaec7> (in
English)
- <http://blog.rom1v.com/2017/03/gnirehtet/> (in French)
