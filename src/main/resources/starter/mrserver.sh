#!/bin/sh
# ==============================================================================
#   MRadmin starter (Linux) - starts the Paper server, brings it back after
#   restarts and crashes and keeps Paper up to date. Settings: mrserver.properties
#   and plugins/MRadmin/config.yml (section server). Guide:
#   https://mrtechnology.marcelr.at/wiki/mradmin/starter/
#
#   New server:   put this file into an empty folder, then:  sh mrserver.sh setup
#   Help:         ./mrserver.sh help
#
#   This file is replaced by MRadmin - do not edit it, use mrserver.properties.
# ==============================================================================
cd "$(dirname "$0")" || exit 1
DIR=plugins/MRadmin/starter
JAR=$DIR/starter.jar
JAVA=$(sed -n 's/^java=//p' mrserver.properties 2>/dev/null | tr -d '\015' | tail -n 1)
[ -n "$JAVA" ] || JAVA=java

if ! command -v "$JAVA" >/dev/null 2>&1 && [ ! -x "$JAVA" ]; then
    echo "Java not found ($JAVA). Paper needs Java 25 or newer, e.g.: sudo apt install openjdk-25-jre-headless"
    exit 1
fi

# First setup: no starter yet - use MRadmin from plugins/ or download the newest stable release.
if [ ! -f "$JAR" ]; then
    PLUGIN=$(ls plugins/MRadmin*.jar 2>/dev/null | tail -n 1)
    DOWNLOADED=""
    if [ -z "$PLUGIN" ]; then
        echo "Downloading MRadmin ..."
        API=https://api.github.com/repos/therealmarci/mradmin/releases/latest
        if command -v curl >/dev/null 2>&1; then
            JSON=$(curl -fsSL "$API")
        else
            JSON=$(wget -qO- "$API")
        fi
        URL=$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*/MRadmin-mc[^"]*\.jar"' | head -n 1 \
            | sed 's/.*"\(https:[^"]*\)"$/\1/')
        if [ -z "$URL" ]; then
            echo "Could not find the MRadmin download. Put MRadmin into plugins/ and run this again."
            exit 1
        fi
        mkdir -p plugins
        PLUGIN="plugins/${URL##*/}"
        if command -v curl >/dev/null 2>&1; then
            curl -fsSL -o "$PLUGIN" "$URL" || { rm -f "$PLUGIN"; exit 1; }
        else
            wget -qO "$PLUGIN" "$URL" || { rm -f "$PLUGIN"; exit 1; }
        fi
        DOWNLOADED="--downloaded"
    fi
    [ "$1" = setup ] && shift
    exec "$JAVA" -jar "$PLUGIN" setup $DOWNLOADED "$@"
fi

if [ "$1" = run ]; then
    # Loop only for a new starter version (exit code 3) - restarts of the server happen inside the starter.
    export MRSERVER_WRAPPER=1
    while :; do
        [ -f "$DIR/starter-new.jar" ] && mv -f "$DIR/starter-new.jar" "$JAR"
        "$JAVA" -jar "$JAR" run
        code=$?
        [ "$code" -eq 3 ] || exit "$code"
    done
fi

exec "$JAVA" -jar "$JAR" "$@"
