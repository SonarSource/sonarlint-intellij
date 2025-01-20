/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.util;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.util.SonarLintUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarLintUtilsTests extends AbstractSonarLintLightTests {

  private static final String URL_WITH_SLASH = "https://sonarqube.com/next/";
  private static final String URL_WITHOUT_SLASH = "https://sonarqube.com/next";

  private final VirtualFile testFile = mock(VirtualFile.class);
  private final FileType binary = mock(FileType.class);
  private final FileType notBinary = mock(FileType.class);

  @BeforeEach
  void prepare() {
    when(binary.isBinary()).thenReturn(true);

    when(testFile.getParent()).thenReturn(mock(VirtualFile.class));
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.isValid()).thenReturn(true);
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.getFileType()).thenReturn(notBinary);
  }

  @Test
  void testFailGetComponent() {
    var throwable = catchThrowable(() -> SonarLintUtils.getService(getProject(), SonarLintUtilsTests.class));

    // in intellij 14, container.getComponent will throw an exception itself, so we can't assert the exact exception type and message
    assertThat(throwable).isInstanceOf(Throwable.class);
  }

  @Test
  void testIsBlank() {
    assertThat(SonarLintUtils.isBlank("")).isTrue();
    assertThat(SonarLintUtils.isBlank("  ")).isTrue();
    assertThat(SonarLintUtils.isBlank(null)).isTrue();
    assertThat(SonarLintUtils.isBlank(" s ")).isFalse();
  }

  @Test
  void testIsEmpty() {
    assertThat(SonarLintUtils.isEmpty("")).isTrue();
    assertThat(SonarLintUtils.isEmpty("  ")).isFalse();
    assertThat(SonarLintUtils.isEmpty(null)).isTrue();
    assertThat(SonarLintUtils.isEmpty(" s ")).isFalse();
  }

  @Test
  void testWithoutTrailingSlash() {
    assertThat(SonarLintUtils.withoutTrailingSlash(URL_WITH_SLASH)).isEqualTo(URL_WITHOUT_SLASH);
    assertThat(SonarLintUtils.withoutTrailingSlash(URL_WITHOUT_SLASH)).isEqualTo(URL_WITHOUT_SLASH);
  }

  @Test
  void testWithTrailingSlash() {
    assertThat(SonarLintUtils.withTrailingSlash(URL_WITH_SLASH)).isEqualTo(URL_WITH_SLASH);
    assertThat(SonarLintUtils.withTrailingSlash(URL_WITHOUT_SLASH)).isEqualTo(URL_WITH_SLASH);
  }

}
