#!/bin/bash
set -xe

/etc/init.d/xvfb start
fluxbox &
x11vnc -nopw -viewonly -forever -display $DISPLAY &
/opt/idea/bin/idea.sh