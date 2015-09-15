#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v16 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools
build_snapshot "SonarSource/sonarqube"
build_snapshot "SonarSource/sonar-runner"

./gradlew buildPlugin
