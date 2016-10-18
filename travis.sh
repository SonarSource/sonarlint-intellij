#!/bin/bash
#
# Requires the environment variables:
# - SONAR_HOST_URL: URL of SonarQube server
# - SONAR_TOKEN: access token to send analysis reports to $SONAR_HOST_URL
# - GITHUB_TOKEN: access token to send analysis of pull requests to GibHub
# - ARTIFACTORY_URL: URL to Artifactory repository
# - ARTIFACTORY_DEPLOY_REPO: name of deployment repository
# - ARTIFACTORY_DEPLOY_USERNAME: login to deploy to $ARTIFACTORY_DEPLOY_REPO
# - ARTIFACTORY_DEPLOY_PASSWORD: password to deploy to $ARTIFACTORY_DEPLOY_REPO

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v28 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

function prepareBuildVersion {
    # Analyze with SNAPSHOT version as long as SQ does not correctly handle purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    # Deply the release version related to this build instead of snapshot
    sed -i.bak "s/-SNAPSHOT/-build$TRAVIS_BUILD_NUMBER/g" gradle.properties
    # set the build name with travis build number
    echo buildInfo.build.name=sonarlint-intellij >> gradle.properties 
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties
}

unset DISPLAY

case "$TARGET" in

CI)
  if [ "${TRAVIS_BRANCH}" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    strongEcho 'Build and analyze commit in master and publish in artifactory'
    # this commit is master must be built and analyzed (with upload of report)
    git fetch --unshallow || true
    prepareBuildVersion
    ./gradlew buildPlugin check sonarqube artifactory \
        -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace --info \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.projectVersion=$CURRENT_VERSION \
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

  else
    strongEcho 'Build, no analysis'
    # Build branch, without any analysis

    ./gradlew buildPlugin check -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace

  fi
  ;;


*)
  echo "Unexpected TARGET value: $TARGET"
  exit 1
  ;;

esac



