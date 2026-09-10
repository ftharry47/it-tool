#!/bin/bash
set -e

if ! command -v mvn &> /dev/null; then
    echo "Maven (mvn) was not found on PATH. Install Maven and try again."
    exit 1
fi

mvn clean package

rm -f target/app.zip
zip -j target/app.zip target/app.jar startup.sh

echo "Created target/app.zip"
