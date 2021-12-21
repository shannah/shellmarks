#!/bin/bash

if [ ! -z "$button1Clicked" ]; then
echo "Button 1 was clicked"
fi
if [ ! -z "$button2Clicked" ]; then
echo "Button 2 was clicked"
fi
if [ ! -z "$button3Clicked" ]; then
echo "Button 3 was clicked"
fi
exit 0
---
# The script title
__title__="Buttons Sample"

# Script description in Asciidoc format
__description__='''
This example shows how to use the 'button' field type to add multiple submit buttons on your dialog.
'''

# Tags used to place script into one or more sections of the catalog
__tags__="#samples"

[button1Clicked]
label="Button 1"
type="button"
disposeOnSubmit=false

[button2Clicked]
label="Button 2"
type="button"

[button3Clicked]
label="Button 3"
type="button"

