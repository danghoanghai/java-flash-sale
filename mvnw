#!/bin/sh
# Maven Wrapper script — downloads Maven if not present, then delegates.
# Simplified version for Docker builds.

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

# Read distributionUrl from properties
distributionUrl=$(grep 'distributionUrl' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)

MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven"

if [ ! -d "$MAVEN_HOME" ]; then
    mkdir -p "$MAVEN_HOME"
    echo "Downloading Maven from $distributionUrl"
    if command -v wget > /dev/null; then
        wget -q -O /tmp/maven.zip "$distributionUrl"
    elif command -v curl > /dev/null; then
        curl -sL -o /tmp/maven.zip "$distributionUrl"
    else
        echo "Error: wget or curl is required" >&2
        exit 1
    fi
    unzip -q -o /tmp/maven.zip -d "$MAVEN_HOME"
    rm -f /tmp/maven.zip
fi

MAVEN_BIN=$(find "$MAVEN_HOME" -name 'mvn' -path '*/bin/mvn' | head -1)

if [ -z "$MAVEN_BIN" ]; then
    echo "Error: Could not find mvn binary after extraction" >&2
    exit 1
fi

exec "$MAVEN_BIN" "$@"
