#!/bin/bash
# This script uses pytube to download videos from Youtube into the Downloads directory
# Requires that you have pytube installed:
# pip install pytube
set -e
pytubePath=$(which pytube)
if [ -z "$pytubePath" ]; then
    echo "This script requires pytube"
    open https://pytube.io/en/latest/user/install.html
    exit 1
fi
cd "$HOME/Downloads"
pytube "${videoUrl}"
fileName=$(ls -t | /usr/bin/head -n 1);
open "$fileName"
exit 0
---
# The script title
__title__="PyTube"

# Script description in Asciidoc format
__description__='''
Paste a Youtube URL into the field below and press "Download"

The video will be downloaded to the file:/Users/shannah/Downloads[Downloads] directory
'''

# Doc string.  In asciidoc format.  Displayed in Shellmarks catalog
__doc__='''
This script uses PyTube to download Youtube videos to the https://open//Users/shannah/Downloads[Downloads] directory

IMPORTANT: This script requires that you have https://pytube.io/en/latest/user/install.html[pytube] installed.
'''

# Tags used to place script into one or more sections of the catalog
__tags__="#youtube"

[videoUrl]
  label="Video URL"
  help="Paste the Youtube URL into this field"
  required=true

[download]
  label="Download"
  type="button"
