# Shellmarks
Simple GUI Wrappers for Shell Scripts

## Synopsis

Shellmarks is a shell script wrapper that allows you to set environment variables via a GUI form prior to the script's execution.

The GUI form is defined using [TOML](https://toml.io/en/) inside the shell script.  If the shell script is run using `shellmarks`, it will first check for such a form definition in the script, and display it to the user.  The user then fills in the form, and presses "Run".  It then runs the script using the script's desired interpreter (as specified by its `#!`) with the user's input in the script's environment.

## Hello World

The following is a simple script that prints _hello ${name}_, where _${name}_ is provided by the user.

```bash
#!/bin/bash
echo "Hello ${name}"
exit 0
<shellmarks>
[name]
  type="text"
  label="Please enter your name"
  required=true
</shellmarks>
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

- Getting Started Guide
- Command-line Options
- GUI Form Options
- Sample Scripts


