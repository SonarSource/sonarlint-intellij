SonarQube for IntelliJ Plugin — DevoxxGenie Fork
=================================================

![SonarMeetsDevoxxGenie](https://github.com/user-attachments/assets/4c32019b-cdca-4973-a339-454ee91fc7f2)

> **Fork notice:** This is a fork of [SonarSource/sonarlint-intellij](https://github.com/SonarSource/sonarlint-intellij) that adds integration with the [DevoxxGenie](https://plugins.jetbrains.com/plugin/24169-devoxxgenie) IntelliJ plugin. When DevoxxGenie is installed, SonarLint issues can be sent directly to DevoxxGenie for AI-assisted fix suggestions.

[![Build Status](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml?query=branch%3Amaster)
[![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij)

SonarQube for IDE is an IDE extension that helps you detect and fix quality issues, ensuring you
deliever [integrated code quality and security](https://www.sonarsource.com/solutions/for-developers/).
Like a spell checker, SonarQube for IntelliJ squiggles flaws so they can be fixed before committing code.

DevoxxGenie Integration
-----------------------

This fork adds a seamless bridge between SonarLint findings and the DevoxxGenie AI coding assistant. When the [DevoxxGenie plugin](https://plugins.jetbrains.com/plugin/24169-devoxxgenie) is installed alongside SonarQube for IntelliJ, two new entry points become available:

https://github.com/user-attachments/assets/c2355204-f236-4b30-a75f-799da54c14a7

### Editor Intention Action

When your cursor is on a SonarLint issue, the lightbulb menu includes a **"DevoxxGenie: Fix '...'"** action. Selecting it sends the issue details and surrounding code context to DevoxxGenie, which generates a fix suggestion using the LLM provider you have configured.

<img width="909" height="267" alt="Screenshot 2026-02-16 at 12 24 31" src="https://github.com/user-attachments/assets/bc9b0fbf-4f5d-49eb-a233-2050d7bac47a" />

### Rule Description Panel Button

When viewing a finding in the SonarLint tool window, a styled **"Fix with DevoxxGenie"** button appears in the rule header panel. Clicking it sends a structured prompt containing:

- The rule name and key
- The issue message
- The file path and line number
- A code snippet with surrounding context (~20 lines)

<img width="1794" height="300" alt="Screenshot 2026-02-16 at 12 21 09" src="https://github.com/user-attachments/assets/eadd62d9-9533-4192-a213-49cb1aa89e23" />

### Task Creation

The report panel toolbar includes a **"Create DevoxxGenie Task(s)"** button. Select one or more issues using the checkboxes next to each finding, then click the button to generate structured backlog task files in `backlog/tasks/`. Each file is formatted with YAML frontmatter and a markdown body that DevoxxGenie's CLI Runner can pick up directly:

- Rule key, severity, file path, and line number are included
- Task IDs are kept in sync with any existing DevoxxGenie tasks by scanning `backlog/tasks/`, `backlog/completed/`, and `backlog/archive/tasks/`

<img width="1273" height="292" alt="TaskCreation" src="https://github.com/user-attachments/assets/cc64aa52-09b2-40e3-a52b-84a7b72abdf8" />

### How It Works

The integration uses a reflective bridge (`DevoxxGenieBridge`) to communicate with DevoxxGenie's `ExternalPromptService` at runtime. This means:

- **No compile-time dependency** on DevoxxGenie — the plugin works normally without it
- **Automatic detection** — the "Fix with DevoxxGenie" options only appear when the DevoxxGenie plugin is installed and enabled
- **Any LLM provider** — DevoxxGenie supports multiple LLM providers (OpenAI, Anthropic, Ollama, etc.), so the fix suggestions use whichever provider you have configured

### Requirements

- SonarQube for IntelliJ (this plugin)
- [DevoxxGenie](https://plugins.jetbrains.com/plugin/24169-devoxxgenie) plugin installed (v0.9.12 or higher) and configured with an LLM provider

Useful links
------------

- [Documentation](https://docs.sonarsource.com/sonarqube-for-intellij/)
    - A full list of supported programming languages and links to the static code analysis rules associated with each language are available
      on the [Rules page](https://docs.sonarsource.com/sonarqube-for-intellij/using/rules/).
- [Community](https://community.sonarsource.com/c/help/sl)
    - Report an issue, ask for some help, or suggest new features.
- [DevoxxGenie](https://github.com/devoxx/DevoxxGenieIDEAPlugin) — the AI coding assistant plugin this fork integrates with

How to install
--------------

You can install SonarQube for IntelliJ from the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/7973-sonarlint), directly
available in the IDE preferences.

Full up-to-date details are available on the [Requirements](https://docs.sonarsource.com/sonarqube-for-intellij/getting-started/requirements/)
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

The project includes automated plugin verification across multiple JetBrains IDEs using the IntelliJ Platform Plugin Verifier. This ensures compatibility across different IDE
versions and types.

### Automated Nightly Testing

Plugin verification runs automatically every night via CI/CD pipeline across three different environments:

- **EAP**: Tests against Early Access Program (pre-release) versions
- **MINIMAL**: Tests against the oldest supported release version
- **LATEST**: Tests against the latest release versions

### Supported IDEs

The verification covers most of the major JetBrains IDEs that we support:

- Android Studio
- CLion
- GoLand
- IntelliJ IDEA (Community & Ultimate)
- PhpStorm
- PyCharm (Community & Professional)
- Rider
- RubyMine
- WebStorm

### Running Verification Locally

To run plugin verification locally:

```bash
# Use recommended IDE versions (default)
./gradlew :verifyPlugin

# Test against specific environment
./gradlew :verifyPlugin -PverifierEnv=LATEST

# For regular CI builds, only minimal version of IC
./gradlew :verifyPlugin -PverifierEnv=CI
```

License
-------

Copyright 2015-2025 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
