#!/bin/bash

echo "准备开始打包 autotracker-gradle-plugin ..."
export IS_EXCLUDE_DEMOS=true
./gradlew clean \
&& ./gradlew :growingio-autotracker-gradle-plugin:publishReleasePublicationToMavenLocal \
&& ./gradlew clean \
&& export IS_EXCLUDE_DEMOS=false