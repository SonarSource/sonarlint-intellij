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
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v40 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

function prepareBuildVersion {
    # Analyze with SNAPSHOT version as long as SQ does not correctly handle purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
    # In case of 2 digits, we need to add the 3rd digit (0 obviously)
    # Mandatory in order to compare versions (patch VS non patch)
    IFS=$'.'
    DIGIT_COUNT=`echo $RELEASE_VERSION | wc -w`
    unset IFS
    if [ $DIGIT_COUNT -lt 3 ]; then
        RELEASE_VERSION="$RELEASE_VERSION.0"
    fi
    NEW_VERSION="$RELEASE_VERSION.$TRAVIS_BUILD_NUMBER"
    export PROJECT_VERSION=$NEW_VERSION

    # Deply the release version related to this build instead of snapshot
    sed -i.bak "s/$CURRENT_VERSION/$NEW_VERSION/g" gradle.properties
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
        -Dsonar.login=$SONAR_TOKEN \
        -Dsonar.analysis.buildNumber=$TRAVIS_BUILD_NUMBER \
        -Dsonar.analysis.pipeline=$TRAVIS_BUILD_NUMBER \
        -Dsonar.analysis.sha1=$TRAVIS_COMMIT  \
        -Dsonar.analysis.repository=$TRAVIS_REPO_SLUG
  
  elif [[ "${TRAVIS_BRANCH}" == "branch-"* ]] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    strongEcho 'Build and publish in artifactory'
    prepareBuildVersion
    ./gradlew buildPlugin check artifactory \
        -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace --info

  elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ -n "${GITHUB_TOKEN-}" ]; then
    strongEcho 'Build and analyze pull request'                                                                                                                              
    # this pull request must be built and analyzed
    ./gradlew buildPlugin check sonarqube \
        -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HeadlessToolkit --stacktrace \
        -Dsonar.analysis.mode=issues \
        -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
        -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.github.oauth=$GITHUB_TOKEN \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

    ./gradlew sonarqube \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN \
        -Dsonar.analysis.buildNumber=$TRAVIS_BUILD_NUMBER \
        -Dsonar.analysis.pipeline=$TRAVIS_BUILD_NUMBER \
        -Dsonar.analysis.sha1=$TRAVIS_PULL_REQUEST_SHA  \
        -Dsonar.analysis.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.analysis.prNumber=$TRAVIS_PULL_REQUEST \
        -Dsonar.branch.name=$TRAVIS_PULL_REQUEST_BRANCH \
        -Dsonar.branch.target=$TRAVIS_BRANCH

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



