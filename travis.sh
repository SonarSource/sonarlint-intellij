#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v21 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

build_snapshot SonarSource/sonar-runner

if [ -n "${PR_ANALYSIS:-}" ] && [ "${PR_ANALYSIS}" == true ]
then
  if [ "$TRAVIS_PULL_REQUEST" != "false" ]
  then
    # For security reasons environment variables are not available on the pull requests
    # coming from outside repositories
    # http://docs.travis-ci.com/user/pull-requests/#Security-Restrictions-when-testing-Pull-Requests
    if [ -n "$SONAR_GITHUB_OAUTH" ]; then

      # PR analysis
      # Accessing Dory with SSL requires Java 8
      source $HOME/.jdk_switcher_rc
      jdk_switcher use oraclejdk8    
      ./gradlew sonarqube \
        -Dsonar.analysis.mode=issues \
        -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
        -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.github.login=$SONAR_GITHUB_LOGIN \
        -Dsonar.github.oauth=$SONAR_GITHUB_OAUTH \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_LOGIN \
        -Dsonar.password=$SONAR_PASSWD
    fi
  fi
else
  # Regular CI
  unset DISPLAY
  ./gradlew buildPlugin check -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit -PijVersion=$IJ_VERSION --debug
fi




