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
package org.sonarlint.intellij.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarLintUtilsTest extends LightPlatformCodeInsightFixtureTestCase {
  private VirtualFile testFile;
  private VirtualFile binaryFile;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testFile = myFixture.addFileToProject("testFile.java", "dummy").getVirtualFile();
    binaryFile = myFixture.addFileToProject("test.bin", "dummy").getVirtualFile();
  }

  @Test
  public void testShouldAnalyze() {
    assertThat(SonarLintUtils.shouldAnalyze(testFile, myModule)).isTrue();

    assertThat(SonarLintUtils.shouldAnalyze(null, myModule)).isFalse();
    assertThat(SonarLintUtils.shouldAnalyze(testFile, null)).isFalse();

    assertThat(SonarLintUtils.shouldAnalyze(binaryFile, myModule)).isTrue();
  }

  @Test
  public void testShouldAnalyzeDisposed() {
    Project disposed = mock(Project.class);
    Module module = mock(Module.class);

    when(disposed.isDisposed()).thenReturn(true);
    when(module.getProject()).thenReturn(disposed);

    assertThat(SonarLintUtils.shouldAnalyze(testFile, module)).isFalse();
  }

  @Test
  public void testShouldAnalyzeInvalid() {
    VirtualFile f = mock(VirtualFile.class);
    when(f.isValid()).thenReturn(false);

    assertThat(SonarLintUtils.shouldAnalyze(f, myModule)).isFalse();
  }
}
