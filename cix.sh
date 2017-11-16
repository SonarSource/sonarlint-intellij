#!/bin/bash
set -euo pipefail
echo "Running with IDEA_VERSION=$IDEA_VERSION"
    
./gradlew buildPlugin check -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit -PijVersion=$IDEA_VERSION --stacktrace

echo "\n\n#### Running ITs ####\n\n"

./gradlew -p its check --stacktrace

