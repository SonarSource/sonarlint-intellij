#!/bin/sh

echo "IntelliJ home path: "
read intellij_home

echo "IntelliJ version: "
read intellij_version


if [ -d "$intellij_home" ]; then
  mvn install:install-file -Dfile=$intellij_home/lib/annotations.jar -DgroupId=com.intellij -DartifactId=annotations -Dversion=$intellij_version -Dpackaging=jar
  mvn install:install-file -Dfile=$intellij_home/lib/openapi.jar -DgroupId=com.intellij -DartifactId=openapi -Dversion=$intellij_version -Dpackaging=jar
  mvn install:install-file -Dfile=$intellij_home/lib/util.jar -DgroupId=com.intellij -DartifactId=util -Dversion=$intellij_version -Dpackaging=jar
fi
