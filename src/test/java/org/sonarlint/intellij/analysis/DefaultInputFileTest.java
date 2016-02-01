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

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultInputFileTest {
  private DefaultInputFile inputFile;

  @Test
  public void roundTrip() {
    Path p = mock(Path.class);
    Charset c = mock(Charset.class);
    inputFile = new DefaultInputFile(p, true, c);

    assertThat(inputFile.charset()).isEqualTo(c);
    assertThat(inputFile.isTest()).isTrue();
    assertThat(inputFile.path()).isEqualTo(p);
  }
}
