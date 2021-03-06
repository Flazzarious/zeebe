#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -o -B -T$LIMITS_CPU -s .ci/settings.xml verify -P skip-unstable-ci,parallel-tests -pl qa/integration-tests -DtestMavenId=2
