/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import org.jdom.JDOMException;
import org.junit.Test;
import org.sonarlint.intellij.common.analysis.ExcludeResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Heavy because of the need to close the project
public class LocalFileExclusionsTest extends HeavyPlatformTestCase {
  private FileType type = mock(FileType.class);
  private VirtualFile testFile = mock(VirtualFile.class);
  private BooleanSupplier powerModeCheck = mock(BooleanSupplier.class);
  private LocalFileExclusions exclusions;
  private String projectFilePath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    projectFilePath = myProject.getProjectFilePath();
    exclusions = new LocalFileExclusions(getProject());
    when(powerModeCheck.getAsBoolean()).thenReturn(false);
    when(type.isBinary()).thenReturn(false);
    when(testFile.getParent()).thenReturn(mock(VirtualFile.class));
    when(testFile.getFileType()).thenReturn(type);
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.isValid()).thenReturn(true);
  }

  @Override
  protected void tearDown() throws Exception {
    if (!myProject.isOpen()) {
      myProject = ProjectManager.getInstance().loadAndOpenProject(projectFilePath);
    }
    super.tearDown();
  }

  @Test
  public void test_should_not_analyze_automatically_if_module_is_null() {
    ExcludeResult result = exclusions.canAnalyze(testFile, null);
    assertThat(result.isExcluded()).isTrue();
  }

  @Test
  public void test_should_analyze_file() {
    assertThat(exclusions.canAnalyze(testFile, getModule()).isExcluded()).isFalse();
  }

  @Test
  public void test_should_not_analyze_if_file_is_binary() {
    when(type.isBinary()).thenReturn(true);
    assertThat(exclusions.canAnalyze(testFile, getModule()).isExcluded()).isTrue();
  }

  @Test
  public void test_should_not_analyze_if_module_is_null() {
    assertThat(exclusions.canAnalyze(testFile, null).isExcluded()).isTrue();
  }

  @Test
  public void test_should_not_analyze_if_project_is_disposed() throws IOException, JDOMException {
    PlatformTestUtil.forceCloseProjectWithoutSaving(myProject);

    assertThat(exclusions.canAnalyze(testFile, getModule()).isExcluded()).isTrue();
  }

  @Test
  public void test_should_not_analyze_if_file_is_invalid() {
    VirtualFile f = mock(VirtualFile.class);
    when(f.isValid()).thenReturn(false);

    assertThat(exclusions.canAnalyze(f, getModule()).isExcluded()).isTrue();
  }
}
