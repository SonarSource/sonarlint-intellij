# SonarLint IntelliJ Plugin

[![Build Status](https://travis-ci.org/SonarSource/sonarlint-intellij.svg?branch=master)](https://travis-ci.org/SonarSource/sonarlint-intellij)

## How to build

    ./gradlew buildPlugin

Note that the above won't run tests and checks. To do that too, run:

    ./gradlew check buildPlugin

For the complete list of tasks, see:

    ./gradlew tasks

## How to develop in IntelliJ

- Import the project as a Gradle project
- For debugging, simply execute the Gradle task `runIdea`

## How to release

    ./gradlew release

Deploy on Jetbrains plugin repository (todo try to use publish task).

## More information

**[SonarLint for IntelliJ](http://www.sonarlint.org/intellij/)**
