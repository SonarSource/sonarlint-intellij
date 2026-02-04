SonarQube for IntelliJ Plugin
=========================

[![Build Status](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/SonarSource/sonarlint-intellij/actions/workflows/build.yml?query=branch%3Amaster)
[![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.sonarlint.intellij%3Asonarlint-intellij)

SonarQube for IDE is an IDE extension that helps you detect and fix quality issues, ensuring you
deliever [integrated code quality and security](https://www.sonarsource.com/solutions/for-developers/).
Like a spell checker, SonarQube for IntelliJ squiggles flaws so they can be fixed before committing code.

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

CI/CD and IDE Testing
---------------------

The project uses Docker images to provide consistent IDE installations across CI/CD pipelines. This approach eliminates the need for
downloading IDEs from Artifactory and ensures reproducible builds.
### IDE Provisioning Strategy

The project uses a hybrid approach for IDE provisioning in CI:

- **Embedded IDEs** (2023, 2024 versions): Pre-installed in the Docker container
- **Non-embedded IDEs** (2025+ versions, specific products): Downloaded from Repox on-demand

### Docker Container and Embedded IDEs

The project uses a single Docker image (`sonarlint-intellij`) as a container for all CI jobs. This container includes pre-installed
IDEs:

- **IntelliJ IDEA Community 2023**: `IDEA_2023_DIR`
- **IntelliJ IDEA Ultimate 2023**: `IDEA_ULTIMATE_2023_DIR`
- **CLion 2023**: `CLION_2023_DIR`
- **CLion 2024**: `CLION_2024_DIR` (for ReSharper testing)
- **Rider 2023**: `RIDER_2023_DIR`
- **Rider 2024**: `RIDER_2024_DIR`

These environment variables point to the installation directories within the container.

### QA Matrix Testing

The QA job (`.github/workflows/build.yml`) tests against a matrix of IDE versions. The workflow:

1. **Runs in container**: All Linux jobs execute in the Docker container with embedded IDEs
2. **Determines IDE source**: Checks if the IDE version is embedded or needs downloading
3. **Runs setup-qa-ide.sh**: Sets up the IDE and exports environment variables (IDEA_HOME, RIDER_HOME, etc.)
4. **Gradle uses env vars**: The Gradle build reads these variables to locate the IDE

### Setup Script: setup-qa-ide.sh

Located at `.github/scripts/setup-qa-ide.sh`, this script:

- Detects if the requested IDE version is embedded in the container
- For embedded IDEs: Sets environment variable to container path
- For non-embedded IDEs: Downloads from Repox (with caching), extracts, and sets environment variable
- Supports unified distributions (IDEA 2025.3+, PyCharm 2025.3+)

Usage:

```bash
.github/scripts/setup-qa-ide.sh IC-2025.3.2
```

Sets `IDEA_HOME` to the IDE location.

### Gradle IDE Resolution (its/build.gradle.kts)

The integration tests module resolves IDEs in this order:

1. **Environment variable** (set by setup-qa-ide.sh): `IDEA_HOME`, `RIDER_HOME`, etc.
2. **On CI**: If env var not set, build FAILS (setup-qa-ide.sh should have provided it)
3. **Local development**: If env var not set, falls back to downloading from Repox

This ensures CI never masks setup failures by downloading IDEs.

### Adding or Changing IDE Versions

#### To Add an Embedded IDE Version

1. **Update Docker image**: Add the IDE to the `sonarlint-intellij` Docker image
   in [docker-images repository](https://github.com/SonarSource/docker-images)
2. **Set environment variable**: The Dockerfile should export `<IDE>_<YEAR>_DIR` (e.g., `IDEA_2025_DIR`)
3. **Update workflow**: Add the version to the embedded pattern in `.github/workflows/build.yml` (line ~435):
   ```yaml
   case "$IDE_CODE-$IDE_YEAR" in
     IC-2023|IU-2023|CL-2023|CL-2024|RD-2023|RD-2024|IC-2025)  # Add new version here
   ```
4. **Update setup-qa-ide.sh**: Add detection case for the new version (line ~60-100):
   ```bash
   IC-2025)
       if [[ -n "${IDEA_2025_DIR:-}" && -d "${IDEA_2025_DIR}" ]]; then
           EMBEDDED="true"
           IDE_PATH="${IDEA_2025_DIR}"
           ENV_VAR="IDEA_HOME"
       fi
       ;;
   ```
5. **Update QA matrix**: Add the version to the QA matrix in `.github/workflows/build.yml` (line ~310)

#### To Add a Non-Embedded IDE Version

1. **Verify Repox availability**: Ensure the IDE version exists in Repox (check with Infrastructure team)
2. **Update QA matrix**: Add the version to the QA matrix in `.github/workflows/build.yml` (line ~310):
   ```yaml
   - ide_version: "IC-2025.4.1"
     suite: "its"
   ```
3. **No other changes needed**: setup-qa-ide.sh will automatically download from Repox
4. **Test locally**: Run the setup script to verify download works:
   ```bash
   export ARTIFACTORY_URL="..."
   export ARTIFACTORY_USER="..."
   export ARTIFACTORY_ACCESS_TOKEN="..."
   .github/scripts/setup-qa-ide.sh IC-2025.4.1
   ```

#### IDE Version Naming Convention

- Format: `{CODE}-{VERSION}` (e.g., `IC-2025.3.2`, `RD-2024.3.9`)
- Codes: `IC` (IDEA Community), `IU` (IDEA Ultimate), `CL` (CLion), `RD` (Rider), `PY` (PyCharm Pro), `PC` (PyCharm Community), `PS` (
  PhpStorm), `GO` (GoLand)

### Local Development

Developers running integration tests locally have three options:

1. **Set environment variable**: Export the IDE home path:
   ```bash
   export IDEA_HOME=/path/to/idea
   ./gradlew :its:runIdeForUiTests -PijVersion=IC-2025.3.2
   ```

2. **Let Gradle download**: Don't set the env var, Gradle will download from Repox:
   ```bash
   # Requires Artifactory credentials in ~/.gradle/gradle.properties
   ./gradlew :its:runIdeForUiTests -PijVersion=IC-2025.3.2
   ```

3. **Use runIdeDirectory property**: Point directly to an IDE installation:
   ```bash
   ./gradlew :its:runIdeForUiTests -PrunIdeDirectory=/Applications/IntelliJ\ IDEA.app/Contents
   ```

### Download Script: download-ides.sh

Located at `.github/scripts/download-ides.sh`, this script handles downloading a single IDE from Repox:

- Supports unified distributions (2025.3+)
- Downloads to `~/.cache/JetBrains/{Product}/{Version}`
- Verifies product-info.json after extraction
- Called by setup-qa-ide.sh for non-embedded IDEs

Direct usage (usually not needed):
```bash
.github/scripts/download-ides.sh IC 2025.3.2
```

Plugin Verification
--------------------------

The project includes automated plugin verification across multiple JetBrains IDEs using the IntelliJ Platform Plugin Verifier. This ensures
compatibility across different IDE
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
