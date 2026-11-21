#!/bin/sh
# Gradle wrapper launcher (simplified; the Actions workflow uses setup-gradle
# and the wrapper properties in gradle/wrapper/gradle-wrapper.properties).
# If a full wrapper jar is needed, run: gradle wrapper --gradle-version 8.7
exec gradle "$@"
