#!/usr/bin/env python3
import requests
import os
import sys

buildNumber = os.getenv('BUILD_NUMBER')
artifactoryUrl = os.getenv('ARTIFACTORY_URL')
buildInfoUrl = f'{artifactoryUrl}/api/build/sonarlint-intellij/{buildNumber}'
buildInfoResp = requests.get(url=buildInfoUrl, auth=(os.getenv('ARTIFACTORY_API_USER'), os.getenv('ARTIFACTORY_API_KEY')))
buildInfoJson = buildInfoResp.json()

buildInfo = buildInfoJson.get('buildInfo', {})
buildInfoProperties = buildInfo.get('properties', {})
version = buildInfoProperties.get('buildInfo.env.PROJECT_VERSION', 'NOT_FOUND')

xml = f"""<plugins>
  <plugin id="org.sonarlint.idea" url="{artifactoryUrl}/sonarsource/org/sonarsource/sonarlint/intellij/sonarlint-intellij/{version}/sonarlint-intellij-{version}.zip" version="{version}"/>
</plugins>"""

updatePluginsXmlUrl = f"{artifactoryUrl}/sonarsource-public-builds/org/sonarsource/sonarlint/intellij/sonarlint-intellij/updatePlugins.xml"
response = requests.put(url=updatePluginsXmlUrl, data=xml, auth=(os.getenv('ARTIFACTORY_API_USER'), os.getenv('ARTIFACTORY_API_KEY')))
if not response.status_code == 201:
    sys.exit('[!] [{0}] Server Error'.format(response.status_code))
