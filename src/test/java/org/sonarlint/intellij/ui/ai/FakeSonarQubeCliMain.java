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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The fake SonarQube CLI that the backend runs in AiIntegrationsTabLightTests, through the launcher script written by
 * {@link FakeSonarQubeCli}. Only uses the JDK, so it runs with the compiled test classes as the only classpath entry.
 * Its state lives in files next to the launcher, which the tests rewrite.
 */
public final class FakeSonarQubeCliMain {

  static final String STATE_DIRECTORY_PROPERTY = "fake.sonar.directory";
  static final String COMMAND_LOG_PROPERTY = "fake.sonar.commandLog";
  static final String VERSION_PROPERTY = "fake.sonar.version";

  private FakeSonarQubeCliMain() {
  }

  public static void main(String[] args) throws IOException {
    var stateDirectory = Path.of(System.getProperty(STATE_DIRECTORY_PROPERTY));
    var command = String.join(" ", args);
    Files.writeString(Path.of(System.getProperty(COMMAND_LOG_PROPERTY)), command + System.lineSeparator(),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    if (command.equals("--version")) {
      // A damaged installation, see FakeSonarQubeCli.breakInstallation()
      if (Files.exists(stateDirectory.resolve("broken"))) {
        System.exit(1);
      }
      System.out.println(System.getProperty(VERSION_PROPERTY));
    } else if (command.startsWith("system status")) {
      System.out.print(Files.readString(stateDirectory.resolve("status.json")));
    } else if (!command.equals("system reset --force")) {
      // The backend deletes the installation itself after a successful reset
      System.err.println("fake sonar: unsupported command " + command);
      System.exit(1);
    }
  }
}
