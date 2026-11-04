/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The fake SonarQube CLI that the backend and the IDE terminal run in the AI Integrations light tests, through the
 * launcher script written by {@link FakeSonarQubeCli}. Only uses the JDK, so it runs with the compiled test classes as
 * the only classpath entry. Its state lives in files next to the launcher, which the tests may also write.
 */
public final class FakeSonarQubeCliMain {

  static final String STATE_DIRECTORY_PROPERTY = "fake.sonar.directory";
  static final String COMMAND_LOG_PROPERTY = "fake.sonar.commandLog";
  static final String VERSION_PROPERTY = "fake.sonar.version";

  /** Marks a damaged installation: `sonar --version` fails. */
  static final String BROKEN_FILE = "broken";
  /** Present when signed in; holds the organization. */
  static final String AUTHENTICATION_FILE = "authentication";
  /** One integrated agent id per line, as `sonar system status` reports them. */
  static final String INTEGRATIONS_FILE = "integrations";
  /** Holds an exit code: the next `auth login` or `integrate` fails with it and changes nothing. */
  static final String FAIL_NEXT_COMMAND_FILE = "fail-next-command";

  static final String DEFAULT_ORGANIZATION = "fake-org";
  private static final String DEFAULT_SERVER = "https://sonarcloud.io";

  /** `sonar integrate` takes the agent's command name; `sonar system status` reports its integration id. */
  private static final Map<String, String> INTEGRATION_ID_BY_TARGET = Map.of("claude", "claude-code");

  private FakeSonarQubeCliMain() {
  }

  public static void main(String[] args) throws IOException {
    var stateDirectory = Path.of(System.getProperty(STATE_DIRECTORY_PROPERTY));
    var command = String.join(" ", args);
    Files.writeString(Path.of(System.getProperty(COMMAND_LOG_PROPERTY)), command + System.lineSeparator(),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    if (command.equals("--version")) {
      if (Files.exists(stateDirectory.resolve(BROKEN_FILE))) {
        System.exit(1);
      }
      System.out.println(System.getProperty(VERSION_PROPERTY));
    } else if (command.startsWith("system status")) {
      System.out.print(status(stateDirectory));
    } else if (command.startsWith("auth login")) {
      failIfRequested(stateDirectory);
      var organization = optionValue(args, "--org");
      Files.writeString(stateDirectory.resolve(AUTHENTICATION_FILE), organization == null ? DEFAULT_ORGANIZATION : organization);
      System.out.println("Signed in");
    } else if (args.length >= 2 && args[0].equals("integrate")) {
      failIfRequested(stateDirectory);
      var integrationId = INTEGRATION_ID_BY_TARGET.getOrDefault(args[1], args[1]);
      Files.writeString(stateDirectory.resolve(INTEGRATIONS_FILE), integrationId + System.lineSeparator(),
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      System.out.println("Integrated " + integrationId);
    } else if (!command.equals("system reset --force")) {
      // The backend deletes the installation itself after a successful reset
      System.err.println("fake sonar: unsupported command " + command);
      System.exit(1);
    }
  }

  private static void failIfRequested(Path stateDirectory) throws IOException {
    var failure = stateDirectory.resolve(FAIL_NEXT_COMMAND_FILE);
    if (Files.exists(failure)) {
      var exitCode = Integer.parseInt(Files.readString(failure).trim());
      Files.delete(failure);
      System.err.println("fake sonar: failing as requested");
      System.exit(exitCode);
    }
  }

  private static String optionValue(String[] args, String option) {
    var index = Arrays.asList(args).indexOf(option);
    return index >= 0 && index + 1 < args.length ? args[index + 1] : null;
  }

  /** The JSON that the real `sonar system status` prints, which the backend decodes. */
  private static String status(Path stateDirectory) throws IOException {
    var authenticationFile = stateDirectory.resolve(AUTHENTICATION_FILE);
    var auth = Files.exists(authenticationFile)
      ? ("{\"status\":\"authenticated\",\"server\":\"" + DEFAULT_SERVER + "\",\"org\":\"" + Files.readString(authenticationFile).trim()
        + "\",\"token\":\"active\"}")
      : "{\"status\":\"unauthenticated\"}";
    var integrationsFile = stateDirectory.resolve(INTEGRATIONS_FILE);
    List<String> integrationIds = Files.exists(integrationsFile)
      ? Files.readAllLines(integrationsFile, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).distinct().toList()
      : new ArrayList<>();
    var integrations = integrationIds.stream()
      .map(id -> "{\"id\":\"" + id + "\",\"path\":\"/fake/" + id + "\",\"mcp\":{\"configured\":true,\"valid\":true},"
        + "\"hooks\":{\"configured\":true,\"valid\":true}}")
      .collect(Collectors.joining(","));
    return "{\"version\":\"" + System.getProperty(VERSION_PROPERTY) + "\",\"auth\":" + auth + ",\"integrations\":[" + integrations + "]}";
  }
}
