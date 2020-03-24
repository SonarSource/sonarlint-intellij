/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

class TypeScriptSupport {

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";

  private final Path workDir;

  TypeScriptSupport(Path workDir) {
    this.workDir = workDir;
    extractTypeScriptIfNeeded();
  }

  Path getTypeScriptPath() {
    return getTypeScriptInstallPath().resolve("package").resolve("lib");
  }

  private Path getTypeScriptInstallPath() {
    return workDir.resolve("typescript");
  }

  private void extractTypeScriptIfNeeded() {
    // TODO Add proper logging!
    Path tsPath = getTypeScriptInstallPath();
    if (!Files.isDirectory(getTypeScriptPath())) {
      try {
        FileUtils.mkdirs(tsPath);
        InputStream typescriptZip = TypeScriptSupport.class.getResourceAsStream("/typescript/typescript.zip");
        ZipUtils.unzip(typescriptZip, tsPath.toFile());
      } catch(Throwable t) {
        // TODO Log!
      }
    }

  }
}
