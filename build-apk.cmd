@echo off
setlocal

set "BUILD_TYPE=%~1"
if "%BUILD_TYPE%"=="" set "BUILD_TYPE=Debug"

if /I "%BUILD_TYPE%"=="debug" set "BUILD_TYPE=Debug"
if /I "%BUILD_TYPE%"=="release" set "BUILD_TYPE=Release"

if /I not "%BUILD_TYPE%"=="Debug" if /I not "%BUILD_TYPE%"=="Release" (
    echo Usage: build-apk.cmd [debug^|release]
    exit /b 2
)

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 8 not found at "%JAVA_HOME%".
    echo Install it with: winget install --id EclipseAdoptium.Temurin.8.JDK
    exit /b 1
)

set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
if not exist "%ANDROID_HOME%\platforms\android-28\android.jar" (
    echo Android SDK platform 28 is missing.
    exit /b 1
)
if not exist "%ANDROID_HOME%\build-tools\28.0.3\aapt.exe" (
    echo Android SDK build-tools 28.0.3 is missing.
    exit /b 1
)

set "GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-5.4.1-all\3221gyojl5jsh0helicew7rwx\gradle-5.4.1"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo Gradle 5.4.1 is missing from the wrapper cache.
    echo Run Android Studio/Gradle once with Gradle 5.4.1, or update this script to your Gradle 5.4.1 path.
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

call "%GRADLE_HOME%\bin\gradle.bat" --configure-on-demand :app:assemble%BUILD_TYPE%
exit /b %ERRORLEVEL%
