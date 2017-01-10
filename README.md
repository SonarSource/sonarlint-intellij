# SonarLint IntelliJ Plugin

[![Build Status](https://travis-ci.org/SonarSource/sonarlint-intellij.svg?branch=master)](https://travis-ci.org/SonarSource/sonarlint-intellij)

## How to build

    ./gradlew buildPlugin

Note that the above won't run tests and checks. To do that too, run:

    ./gradlew check buildPlugin

For the complete list of tasks, see:

    ./gradlew tasks

## How to develop in IntelliJ

Import the project as a Gradle project.

Note: whenever you change a Gradle setting (for example in `build.gradle`),
don't forget to **Refresh all Gradle projects** in the **Gradle** toolbar.

To run an IntelliJ instance with the plugin installed, execute the Gradle task `runIdea` using the command line,
or the **Gradle** toolbar in IntelliJ, under `Tasks/intellij`.
The instance files are stored under `build/idea-sandbox`.

Keep in mind that the `clean` task will wipe out the content of `build/idea-sandbox`,
so you will need to repeat some setup steps for that instance, such as configuring the JDK.

Whenever you change dependency version, the previous versions are not deleted from the sandbox, and the JVM might not load the version that you expect.
As the `clean` task may be inconvenient, an easier workaround is to delete the jars in the sandbox, for example with:

    find build/idea-sandbox/ -name '*.jar' -delete

## How to release

    ./gradlew release

Deploy on Jetbrains plugin repository (todo try to use publish task).

## More information

**[SonarLint for IntelliJ](http://www.sonarlint.org/intellij/)**

### License

Copyright 2013-2017 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)