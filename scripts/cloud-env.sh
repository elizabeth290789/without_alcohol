#!/usr/bin/env bash
# Source this file in Codex cloud; Android Studio does not need it.
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/android-user}"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/gradle-cache}"
if [ -d '/workspace/tools/jdk-17.0.20.1+1' ]; then
    export JAVA_HOME='/workspace/tools/jdk-17.0.20.1+1'
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
