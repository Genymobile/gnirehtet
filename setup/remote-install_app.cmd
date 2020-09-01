@adb start-server
@adb push -p app.apk /data/local/tmp
@adb shell pm install /data/local/tmp/app.apk
@adb shell rm /data/local/tmp/app.apk
@adb kill-server
@pause
