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
import java.util.List;

/**
 * The wrapper around the real SonarQube CLI that the backend and the IDE terminal run in AiIntegrationsTabLightTests,
 * through the launcher script written by {@link WrappedSonarQubeCli}. It passes every command on to the real CLI, except for what the real one
 * cannot do on demand in a test:
 * <ul>
 *   <li>a damaged installation: `sonar --version` fails;</li>
 *   <li>a failing sign-in: the next `sonar auth login` exits with a given code;</li>
 *   <li>the interactive `sonar auth login`, which needs a browser: it signs in to SonarQube Cloud staging with the
 *   integration tests' token instead.</li>
 * </ul>
 * Only uses the JDK, so it runs with the compiled test classes as the only classpath entry.
 */
public final class WrappedSonarQubeCliMain {

  static final String REAL_EXECUTABLE_PROPERTY = "wrapped.sonar.realExecutable";
  static final String STATE_DIRECTORY_PROPERTY = "wrapped.sonar.directory";
  static final String COMMAND_LOG_PROPERTY = "wrapped.sonar.commandLog";
  static final String SERVER_PROPERTY = "wrapped.sonar.server";
  static final String ORGANIZATION_PROPERTY = "wrapped.sonar.organization";

  /** Marks a damaged installation. */
  static final String BROKEN_FILE = "broken";
  /** Holds an exit code: the next `sonar auth login` fails with it and changes nothing. */
  static final String FAIL_NEXT_SIGN_IN_FILE = "fail-next-sign-in";

  static final String TOKEN_VARIABLE = "SONARCLOUD_IT_TOKEN";

  private WrappedSonarQubeCliMain() {
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    var stateDirectory = Path.of(System.getProperty(STATE_DIRECTORY_PROPERTY));
    Files.writeString(Path.of(System.getProperty(COMMAND_LOG_PROPERTY)), String.join(" ", args) + System.lineSeparator(),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    var arguments = List.of(args);
    String input = null;
    if (arguments.equals(List.of("--version")) && Files.exists(stateDirectory.resolve(BROKEN_FILE))) {
      System.exit(1);
    } else if (isInteractiveSignIn(arguments)) {
      failIfRequested(stateDirectory);
      var token = System.getenv(TOKEN_VARIABLE);
      if (token == null || token.isBlank()) {
        System.err.println("wrapped sonar: " + TOKEN_VARIABLE + " is not set, cannot sign in");
        System.exit(1);
      }
      arguments = List.of("auth", "login", "--with-token",
        "--server", System.getProperty(SERVER_PROPERTY), "--org", System.getProperty(ORGANIZATION_PROPERTY));
      input = token + "\n";
    }
    System.exit(runRealCli(arguments, input));
  }

  private static boolean isInteractiveSignIn(List<String> arguments) {
    return arguments.size() >= 2 && arguments.get(0).equals("auth") && arguments.get(1).equals("login") && !arguments.contains("--with-token");
  }

  private static void failIfRequested(Path stateDirectory) throws IOException {
    var failure = stateDirectory.resolve(FAIL_NEXT_SIGN_IN_FILE);
    if (Files.exists(failure)) {
      var exitCode = Integer.parseInt(Files.readString(failure).trim());
      Files.delete(failure);
      System.err.println("wrapped sonar: failing as requested");
      System.exit(exitCode);
    }
  }

  /** Runs the real CLI in this terminal or process, with {@code input} on its standard input if given. */
  private static int runRealCli(List<String> arguments, String input) throws IOException, InterruptedException {
    var command = new ArrayList<String>();
    command.add(System.getProperty(REAL_EXECUTABLE_PROPERTY));
    command.addAll(arguments);
    var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
    if (input == null) {
      builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
    }
    var process = builder.start();
    if (input != null) {
      try (var stdin = process.getOutputStream()) {
        stdin.write(input.getBytes(StandardCharsets.UTF_8));
      }
    }
    return process.waitFor();
  }
}
