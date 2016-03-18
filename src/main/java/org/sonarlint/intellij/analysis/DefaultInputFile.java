/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.sonarsource.sonarlint.core.client.api.analysis.ClientInputFile;

public class DefaultInputFile implements ClientInputFile {
  private final Path p;
  private final boolean test;
  private final Charset charset;
  private final VirtualFile vFile;

  DefaultInputFile(VirtualFile vFile, boolean isTest, Charset charset) {
    this.p = Paths.get(vFile.getPath());
    this.test = isTest;
    this.charset = charset;
    this.vFile = vFile;
  }

  @Override public Path getPath() {
    return p;
  }

  @Override public boolean isTest() {
    return test;
  }

  @Override public Charset getCharset() {
    return charset;
  }

  @Override public VirtualFile getClientObject() {
    return vFile;
  }
}
