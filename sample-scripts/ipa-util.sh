#!/bin/bash
file_extracted="${file}-extracted"
if [ ! -d "${file_extracted}" ]; then
    if [ ! -f "$file" ]; then
        echo "Cannot find file $file"
        exit 1
    fi
    mkdir "${file_extracted}"
    cp "$file" "${file_extracted}/App.zip"
    cd "${file_extracted}"
    unzip App.zip
else
    cd ${file_extracted}
fi
appname=$(ls Payload | grep '\.app$')
if [ ! -z "$showEntitlements" ]; then
    codesign -d --entitlements :- "Payload/${appname}"
fi
if [ ! -z "$showProvisioningProfile" ]; then
    security cms -D -i "Payload/${appname}/embedded.mobileprovision"
fi
exit 0
<shellmarks>
title="IPA Entitlements"
description='''
<asciidoc>
This script will print out the entitlements and provisioning profile for given .ipa file.

See https://developer.apple.com/library/archive/qa/qa1798/_index.html[Apple Tech Article] for more information.
</asciidoc>
'''
[file]
    type="file"
    label="Select ipa file"
    required=true
    help="Select the .ipa file that you wish to inspect."

[showEntitlements]
    type="checkbox"
    label="Show Entitlements"
    default="true"
    help="Check this to show the ipa entitlements."

[showProvisioningProfile]
    type="checkbox"
    label="Show Provisioning Profile"
    default="true"
    help="Check this to show the ipa provisioning profile details."

</shellmarks>
