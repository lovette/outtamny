#!/bin/sh

if [ -z "$1" ]; then
    echo "Error: Usage: command [ARGS] MNYFILE"
    echo "  MNYFILE: Path to Money data file."
    exit 1
fi

exec java -jar "outtamny.jar" "$@"
