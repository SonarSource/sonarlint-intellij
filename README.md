<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/a23fc7ba-23f0-489a-829d-ed88c0748521/Sonar_Logo_Dark%20Backgrounds.svg">
    <img src="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/82c13eba-d95c-4bb8-8007-7ce77c14e043/Sonar_Logo_Light%20Backgrounds.svg" alt="Sonar logo" width="400">
  </picture>
</p>

# SonarQube for IntelliJ

<p>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/7934b7e0-d989-42aa-9c75-73af00b948b7/SQ_Logo_IDE_Dark%20Backgrounds.svg">
    <img src="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/b839fc43-d33c-44b5-9e14-f9dee4e9a047/SQ_Logo_IDE_Light%20Backgrounds.png" alt="SonarQube for IDE logo" width="400">
  </picture>
</p>

[![Build Status](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml?query=branch%3Amaster)
[![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij)
[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/7973-sonarlint)](https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide)
[![GitHub stars](https://img.shields.io/github/stars/SonarSource/sonarlint-intellij?style=flat)](https://github.com/SonarSource/sonarlint-intellij)
[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](#license)
[![Community forum](https://img.shields.io/badge/community-forum-blue)](https://community.sonarsource.com/c/sl/11)

SonarQube for IntelliJ analyzes code as you edit in JetBrains IDEs, highlighting code quality and security findings where they can be fixed before commit.

This repository contains the source for the IDE plugin. It applies the same analysis to code written by developers and AI assistants, and it can connect to SonarQube Server or SonarQube Cloud to share team rules and settings.

What it does
------------

- Detects code quality and security issues as you edit.
- Highlights issues in the editor and explains why they matter.
- Supports connected analysis with [SonarQube Server](https://www.sonarsource.com/products/sonarqube/server/) and [SonarQube Cloud](https://www.sonarsource.com/products/sonarqube/cloud/).
- Helps verify developer-written and AI-generated code before commit.

Useful links
------------

- [Documentation](https://docs.sonarsource.com/sonarqube-for-intellij/)
    - A full list of supported programming languages and links to the static code analysis rules associated with each language are available
      on the [Rules page](https://docs.sonarsource.com/sonarqube-for-intellij/using/rules/).
- [Community](https://community.sonarsource.com/c/help/sl)
    - Report an issue, ask for some help, or suggest new features.

How to install
--------------

You can install SonarQube for IntelliJ from the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/7973-sonarlint), directly
available in the IDE preferences.

Full up-to-date details are available on
the [Requirements](https://docs.sonarsource.com/sonarqube-for-intellij/getting-started/requirements/)
and [Installation](https://docs.sonarsource.com/sonarqube-for-intellij/getting-started/installation/) pages.

Questions and Feedback?
--------------------------

For SonarQube for IntelliJ support questions ("How do I?", "I got this error, why?", ...), please first read
the [FAQ](https://community.sonarsource.com/t/frequently-asked-questions/7204) to learn how to get your logs, and then head to
the [Sonar forum](https://community.sonarsource.com/c/help/sl). Before creating a new topic, please check if your question has already been
answered because there is a chance that someone has already had the same issue.

Be aware that this forum is a community, and the standard pleasantries are expected (_Hello, Thank you, I appreciate the reply, etc._). If
you don't get an answer to your thread, you should sit on your hands for at least three days before bumping it. Operators are not standing
by, but the Teams and Community Managers know that your questions are important. :-)

Contributing
------------

If you would like to see a new feature, check out the [PM for a Day](https://community.sonarsource.com/c/sl/pm-for-a-day-sl/41) page! There
we provide a forum to discuss your needs and offer you a chance to engage the Product Manager and development teams directly. Feel free to
add to an ongoing discussion or create a new thread if you have something new to bring up.

Please be aware that we are not actively looking for feature contributions. The truth is that it's extremely difficult for someone outside
SonarSource to comply with our roadmap and expectations. Therefore, we typically only accept minor cosmetic changes and typo fixes.

With that in mind, if you would like to submit a code contribution, please create a pull request for this repository. Please explain your
motives to contribute the change, describe what problem you are trying to fix, and tell us what improvement you are trying to make. The
SonarLint Team will review the PR and discuss internally how it aligns with
the [roadmap](https://www.sonarsource.com/products/sonarlint/roadmap/).

Make sure that you follow our [code style](https://github.com/SonarSource/sonar-developer-toolset#code-style-configuration-for-intellij) and
that all tests are passing.

How to build
------------

```bash
./gradlew buildPlugin
```

Note that the above won't run tests and checks. To do that too, run:

```bash
./gradlew check buildPlugin
```

For the complete list of tasks, see:

```bash
./gradlew tasks
```

For external contributors, the project should be guaranteed to build from any specific tag. During the development phase, some
unreleased dependencies not accessible to the public could be used, preventing you from building the project.

How to run UI tests
-------------------

```bash
./gradlew :its:runIdeForUiTests &
```

The above will start an IDE instance with the SonarQube for IntelliJ plugin. Wait for the UI robot server to start, then run the ITs:

```bash
./gradlew :its:check
```

Finally, close the IDE.

To test against a specific version of IntelliJ, the `ijVersion` property can be used, e.g.:

```bash
./gradlew :its:runIdeForUiTests -PijVersion=IC-2025.2 &
```

If you want to use a local installation, you can use the `runIdeDirectory` property to point to the directory of the IDE you want to run:

```bash
./gradlew :its:runIdeForUiTests -PrunIdeDirectory=<path_to_ide> &
```

Please note that the IDE must be in the foreground while tests are executed.

Because some ITs are leveraging SonarCloud, make sure the `SONARCLOUD_IT_TOKEN` env var is defined (you can find the value in our
password management tool).

How to debug UI tests
---------------------

If you want to debug what happens on the test side, you can launch the test in debug mode from the IDE.

If you want to debug what happens in the SonarQube for IntelliJ plugin, you can open the project in IntelliJ, and run the
`its:runIdeForUiTests` task in debug mode (for example, from the Gradle panel on the right).


How to debug SLOOP
------------------

If you want to debug SLOOP:

* open the Run configuration you are using to launch the IDE (`runLocalIde` or `its:runIdeForUiTests`)
* Add an environment variable: `SONARLINT_JVM_OPTS` with the value `-agentlib:jdwp=transport=dt_socket,address=8080,server=y,suspend=n`
* Run the task
* Open the SLCORE project and click `Run` > `Attach to process`
* Choose the SLOOP running process

If you want to plug the debugger as soon as SLOOP is started, you can modify the command above by having `suspend=y`.
This will wait for the debugger to attach the process before starting SLOOP.

How to develop in IntelliJ
--------------------------

Import the project as a Gradle project.

Note: whenever you change a Gradle setting (for example in `build.gradle.kts`),
remember to **Refresh all Gradle projects** in the **Gradle** toolbar.

To run an IntelliJ instance with the plugin installed, execute the Gradle task `runLocalide` using the command line,
or the **Gradle** toolbar in IntelliJ, under `Tasks/intellij platform`.
The instance files are stored under `build/sonarlint-test`.

To run against a specific IDE, you can use the `runIdeDirectory` property to point to the directory of the IDE you want to run.

For example:

```bash
./gradlew :runLocalIde -PrunIdeDirectory=<path_to_ide>
```

Keep in mind that the `clean` task will wipe out the content of `build/`,
so you will need to repeat some setup steps for that instance, such as configuring the JDK.

Plugin Verification
--------------------------

The project includes automated plugin verification across multiple JetBrains IDEs using the IntelliJ Platform Plugin Verifier.
To run it locally:

```bash
./gradlew :verifyPlugin

# For regular CI builds, only minimal version of IC
./gradlew :verifyPlugin -PverifierEnv=CI
```

License
-------

Copyright SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
