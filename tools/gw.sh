#!/bin/bash
# helper: run gradle wrapper with explicit env
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
export GRADLE_USER_HOME="D:\gradle-home"
cd /d/MineBleach/mod
./gradlew "$@"
