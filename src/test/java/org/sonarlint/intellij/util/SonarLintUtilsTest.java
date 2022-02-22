/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.util.SonarLintUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarLintUtilsTest extends AbstractSonarLintLightTests {
  private VirtualFile testFile = mock(VirtualFile.class);
  private FileType binary = mock(FileType.class);
  private FileType notBinary = mock(FileType.class);

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void prepare() {
    when(binary.isBinary()).thenReturn(true);

    when(testFile.getParent()).thenReturn(mock(VirtualFile.class));
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.isValid()).thenReturn(true);
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.getFileType()).thenReturn(notBinary);
  }

  @Test(expected = Throwable.class)
  public void testFailGetComponent() {
    // in intellij 14, container.getComponent will throw an exception itself, so we can't assert the exact exception type and message
    SonarLintUtils.getService(getProject(), SonarLintUtilsTest.class);
  }

  @Test
  public void testIsBlank() {
    assertThat(SonarLintUtils.isBlank("")).isTrue();
    assertThat(SonarLintUtils.isBlank("  ")).isTrue();
    assertThat(SonarLintUtils.isBlank(null)).isTrue();
    assertThat(SonarLintUtils.isBlank(" s ")).isFalse();
  }

  @Test
  public void testIsEmpty() {
    assertThat(SonarLintUtils.isEmpty("")).isTrue();
    assertThat(SonarLintUtils.isEmpty("  ")).isFalse();
    assertThat(SonarLintUtils.isEmpty(null)).isTrue();
    assertThat(SonarLintUtils.isEmpty(" s ")).isFalse();
  }

//  @Test
//  public void testServerConfigurationPassword() {
//    ServerConnection server = ServerConnection.newBuilder()
//      .setHostUrl("http://myhost")
//      .setLogin("token")
//      .setPassword("pass")
//      .build();
//    ConnectedModeEndpoint config = SonarLintUtils.getServerConfiguration(server);
//    assertThat(config.getLogin()).isEqualTo(server.getLogin());
//    assertThat(config.getPassword()).isEqualTo(server.getPassword());
//  }

}
