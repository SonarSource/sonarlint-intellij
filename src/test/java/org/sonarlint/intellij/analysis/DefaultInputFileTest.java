/*
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
import java.nio.file.Paths;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultInputFileTest {
  private DefaultInputFile inputFile;

  @Test
  public void roundTrip() {
    VirtualFile vFile = mock(VirtualFile.class);
    when(vFile.getPath()).thenReturn("file");
    Charset c = mock(Charset.class);
    inputFile = new DefaultInputFile(vFile, true, c);

    assertThat(inputFile.getCharset()).isEqualTo(c);
    assertThat(inputFile.isTest()).isTrue();
    assertThat(inputFile.getPath()).isEqualTo(Paths.get("file"));
    assertThat(inputFile.getClientObject()).isEqualTo(vFile);
  }
}
