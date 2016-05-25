#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v28 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

build_snapshot "SonarSource/sonarlint-core"
unset DISPLAY

case "$TARGET" in

CI)
  if [ "${TRAVIS_BRANCH}" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    strongEcho 'Build and analyze commit in master'
    # this commit is master must be built and analyzed (with upload of report)
    git fetch --unshallow || true
    ./gradlew buildPlugin check sonarqube \
        -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace --info \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ -n "${GITHUB_TOKEN-}" ]; then
    strongEcho 'Build and analyze pull request'                                                                                                                              
    # this pull request must be built and analyzed (without upload of report)                                                                                                
    ./gradlew buildPlugin check sonarqube \
        -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace \
        -Dsonar.analysis.mode=issues \
        -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
        -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.github.oauth=$GITHUB_TOKEN \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  elif [ "$TRAVIS_BRANCH" == "feature/cix" ]; then
    strongEcho 'feature/cix: build analyse deploy on repox'
    
    # Analyze with SNAPSHOT version as long as SQ does not correctly handle
    # purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    # Do not deploy a SNAPSHOT version but the release version related to this build
    sed -i.bak "s/-SNAPSHOT/-build$TRAVIS_BUILD_NUMBER/g" gradle.properties
    # set the build name with travis build number
    echo buildInfo.build.name=sonarlint-intellij >> gradle.properties 
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties 


    ./gradlew build check sonarqube artifactory \
        -Dsonar.projectVersion=$CURRENT_VERSION \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  else
    strongEcho 'Build, no analysis'
    # Build branch, without any analysis

    ./gradlew buildPlugin check -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace

  fi
  ;;

IT)
  ./gradlew buildPlugin check -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit -PijVersion=$IDEA_VERSION --stacktrace

  ;;

*)
  echo "Unexpected TARGET value: $TARGET"
  exit 1
  ;;

esac



