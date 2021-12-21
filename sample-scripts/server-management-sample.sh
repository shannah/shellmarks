#!/bin/bash
# This is an example script that can start, stop, and check status
# of a Xataface project.  It probably isn't reusable, but is a good
# example of how you might automate your server management in Shellmarks
echo "Running with ${serverStatus}"
PORT="9090"
TUXPIN_PATH=/path/to/tuxpin
URL="http://localhost:$PORT"
if [ ! -z "$startServer" ]; then
    ACTIVE=$(lsof -P -i TCP -s TCP:LISTEN | grep "$PORT")
    if [ ! -z "$ACTIVE" ]; then
        if [ ! -z "$openOnStart" ]; then
            open "$URL"
        fi
        exit 0
    fi
    cd "$TUXPIN_PATH"
    xataface start
    if [ ! -z "$openOnStart" ]; then
        open $URL
    fi
fi

if [ ! -z "$stopServer" ]; then
    ACTIVE=$(lsof -P -i TCP -s TCP:LISTEN | grep "$PORT")
    if [ ! -z "$ACTIVE" ]; then
        cd "$TUXPIN_PATH"
        xataface stop
    else
        echo "Server is already stopped"
    fi
fi

if [ ! -z "$serverStatus" ]; then
    ACTIVE=$(lsof -P -i TCP -s TCP:LISTEN | grep "$PORT")
    if [ -z "$ACTIVE" ]; then
        echo "Server is not active"
    else
        echo "Server is currently running on port $PORT"
    fi
fi
exit 0
---
# The script title
__title__="Tuxpin Dev Server"

# Script description in Asciidoc format
__description__='''
This script will start or stop the Tuxpin development server.
It will then open it in a new browser window.

. http://localhost:9090/admin.php[Tuxpin Admin]
. http://localhost:9090/phpmyadmin[PHPmyAdmin]
'''

# Tags used to place script into one or more sections of the catalog
__tags__="#tuxpin"

[startServer]
    type="button"
    label="Start Server"
    help="Start tuxpin server"
    disposeOnSubmit=false


[stopServer]
    type="button"
    label="Stop Server"
    help="Stop tuxpin server"
    disposeOnSubmit=false

[serverStatus]
    type="button"
    label="Server Status"
    help="Check server status"
    disposeOnSubmit=false

[openOnStart]
    type="checkbox"
    label="Open Tuxpin in Browser on Start"

