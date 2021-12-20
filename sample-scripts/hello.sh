#!/bin/bash
echo "Hello ${name}"
exit 0
---
[name]
  type="text"
  label="Please enter your name"
  required=true
