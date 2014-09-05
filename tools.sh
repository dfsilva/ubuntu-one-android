#!/bin/bash

ANDROIDBIN=$(which android)
if test -z "${ANDROIDBIN}"; then
	echo "Android SDK tools not in PATH, fix with:" >&2
	echo "   PATH=\$PATH:/PathToSDK/tools" >&2
	exit 1
fi

GITBIN=$(which git)
if test -z "${GITBIN}"; then
	echo "You need git, fix with:" >&2
	echo "   apt-get install git" >&2
	exit 1
fi

exit 0
