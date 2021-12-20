# Shellmarks
A documentation and GUI generator for your custom shell scripts.

## Synopsis

Shellmarks is a productivity tool for developers who create lots of custom shell scripts, but can't remember where they saved them, how to use them, or both.  It provides:

1. *A GUI markup language* (using [TOML](https://toml.io/en/)) that can be embedded directly into a shell script. When the script is run using shellmarks, it will display a dialog to the user, prompting them to provide some environment variables.  Currently the dialog can contain text fields, file selection fields, and checkbox fields, but more field types can easily be added as needs be.
2. *A searchable catalog of all of your installed scripts*.  The catalog includes documentation for each script, as well as buttons to _Run_, _Edit_, _Clone_, and _Delete_ them.

## License

MIT

## Features

- *GUI Dialog Generation* - Shellmarks makes it easy to add a GUI dialog to a shell script to allow users to enter environment variables and run the script.
- *Shell Script Catalog* - Shellmarks generates a script catalog of all of your custom scripts, along with documentation and UI options to run and edit your scripts.
- *Compatible with Default Shell Interpreters* - Scripts with Shellmarks markup remain fully compatible with the built-in shell script interpreter.  If you run the script directly in, for example, _bash_, it will just run the script normally.  If you run it with _shellmarks_, it will first display a GUI dialog to let the user set up the script's environment, and then run the script in the default interpreter.
- *Multi-language Support* - You can write your shell scripts in any language you like.  Shellmarks just uses the "hashbang" to know which interpreter to send the script to.

## Hello World

The following is a simple script that prints _hello ${name}_, where _${name}_ is provided by the user.

```bash
#!/bin/bash
echo "Hello ${name}"
exit 0
---
[name]
  type="text"
  label="Please enter your name"
  required=true
```

If you run this script using _bash_ directly, it will simply output:

~~~
Hello
~~~

This is because the _${name}_ environment variable is not set.

If you, instead, run this with `shellmarks`, it will prompt the user to enter their name in a GUI dialog:

![Hello World](images/hello-world.png)

If the user enters "Steve" into the text field, and presses "Run", they'll see the following output in the console.

~~~
Hello Steve
~~~

## More Advanced Example

The following script is a more advanced example that involves a few more field types.  The script is one that I wrote to extract the entitlements and provisioning profile information from an IPA file.  I use this script quite frequently to help support users of [Codename One](https://www.codenameone.com) when they run into issues relating to entitlements and certificates on their iOS apps.

```bash
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
---
__title__="IPA Entitlements"
__description__='''
This script will print out the entitlements and provisioning profile for given .ipa file.

See https://developer.apple.com/library/archive/qa/qa1798/_index.html[Apple Tech Article] for more information.
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
```

If this script is in a file named _ipa-tools.sh_, then you can run it via:

~~~
shellmarks ipa-tools.sh
~~~

The GUI dialog looks like:

![ipa-tools](images/ipa-tools.png)

## Script Structure

Shell scripts written for _shellmarks_ are just regular shell scripts.  At the end of the script you simply add a section with:

~~~
exit 0
---
... Your GUI definitions here in TOML format
~~~

Some notes:

We add `exit 0` so that the script exits before reaching the shellmarks GUI configuration.  This ensures that the script will remain compatible with the default shell iterpreter (e.g. bash).

The `---` serves as a dividing line between the script content, and the shellmarks config.

The contents of this tag will be interpreted as [TOML](https://toml.io/en/).

## Installation

Installation requires that you have NodeJS installed, because the installer uses npm.

[Download NodeJS here](https://nodejs.org/en/download/)

Then, open a terminal, and enter:

```bash
sudo npm install -g shellmarks
```

NOTE: On windows you may not require the "sudo" part.  Just `npm install -g shellmarks`

On Mac and Linux the `sudo` is required to give npm access to install it globally.

## Requirements

Shellmarks should run on any modern Windows, Linux, or Mac system.

## Documentation

- [Users Manual](https://shannah.github.io/shellmarks/manual)
- [CLI Usage](https://shannah.github.io/shellmarks/manual/#cli)
- [GUI Form Configuration](https://shannah.github.io/shellmarks/manual/#config)
- [Sample Scripts](sample-scripts)

## Credits

Shellmarks was created by [Steve Hannah](https://sjhannah.com).  It owes a great deal to the Java open source eco-system, as its development would have been much more difficult without the mature set of Maven dependencies.

Notable dependencies:

- *[AsciiDoctor](https://asciidoctor.org/)* - Shellmarks uses AsciiDoctor to generate the HTML used for the script catalog.
- *[JavaFX](https://openjfx.io/)* - The Script catalog interface is written in JavaFX, a top quality cross-platform UI library.
- *[TOML](https://toml.io/en/)* - Shellmarks needed a simple and concise syntax for describing its UI forms, and TOML fit the bill perfectly.  Less verbose than XML and JSON, and easier to work with than yml.  API was simple to use.  Just dropped in a maven dependency, and I was off and running.

See the [pom.xml file](pom.xml) for a full list of dependencies.


