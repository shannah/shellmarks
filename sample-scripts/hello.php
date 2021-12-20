#!/usr/bin/php
<?php
$firstName = @$_ENV['firstName'];
$lastName = @$_ENV['lastName'];
$option1 = @$_ENV['option1'];
$option2 = @$_ENV['option2'];
$selectedFile = @$_ENV['selectedFile'];

echo "Hello ${firstName} ${lastName}";
echo "You selected ${selectedFile}";
if ($option1) {
    echo "Option1 was selected";
}
if ($option2) {
    echo "Option2 was selected";
}
exit(0);
?>
---
# The script title
__title__="hello-php.php"

# Script description in Asciidoc format
__description__='''
This description will be displayed at the top of the form.

It can be multiline and include https://example.com[Links]
'''

# Doc string.  In asciidoc format.  Displayed in Shellmarks catalog
__doc__='''
This will be displayed in the shellmarks catalog.

You can include _asciidoc_ markup, as well as https://www.example.com[links].
'''

# Tags used to place script into one or more sections of the catalog
__tags__="#samples #php"

[firstName]
  label="First Name"
  required=true

[lastName]
  label="Last Name"

[selectedFile]
  label="Please select a file"
  type="file"

[option1]
  label="Option 1"
  type="checkbox"

[option2]
  label="Option 2"
  type="checkbox"

