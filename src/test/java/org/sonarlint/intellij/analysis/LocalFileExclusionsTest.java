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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFileExclusionsTest extends SonarTest {
  private SonarLintGlobalSettings globalSettings = new SonarLintGlobalSettings();
  private SonarLintProjectSettings projectSettings = new SonarLintProjectSettings();
  private ModuleRootManager moduleRootManager = mock(ModuleRootManager.class);
  private FileType type = mock(FileType.class);
  private VirtualFile testFile = mock(VirtualFile.class);
  private Supplier<Boolean> powerModeCheck = mock(Supplier.class);
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);
  private ApplicationInfo info = mock(ApplicationInfo.class);
  private ProjectRootManager projectRootManager = mock(ProjectRootManager.class);
  private LocalFileExclusions exclusions = new LocalFileExclusions(project, globalSettings, projectSettings, appUtils, info, projectRootManager, powerModeCheck);

  @Before
  public void prepare() {
    when(powerModeCheck.get()).thenReturn(false);
    when(type.isBinary()).thenReturn(false);
    when(testFile.getParent()).thenReturn(mock(VirtualFile.class));
    when(testFile.getFileType()).thenReturn(type);
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.isValid()).thenReturn(true);
    when(info.getVersionName()).thenReturn("Intellij IDEA");
    super.register(module, ModuleRootManager.class, moduleRootManager);
  }

  @Test
  public void should_not_analyze_automatically_if_module_is_null() {
    LocalFileExclusions.Result result = exclusions.checkExclusions(testFile, null);
    assertThat(result.isExcluded()).isTrue();
  }

  @Test
  public void should_analyze_file() {
    assertThat(exclusions.canAnalyze(testFile, module).isExcluded()).isFalse();
  }

  @Test
  public void should_not_analyze_if_file_is_binary() {
    when(type.isBinary()).thenReturn(true);
    assertThat(exclusions.canAnalyze(testFile, module).isExcluded()).isTrue();
  }

  @Test
  public void should_not_analyze_if_module_is_null() {
    assertThat(exclusions.canAnalyze(testFile, null).isExcluded()).isTrue();
  }

  @Test
  public void should_not_analyze_if_project_is_disposed() {
    when(project.isDisposed()).thenReturn(true);
    Module module = mock(Module.class);

    when(project.isDisposed()).thenReturn(true);
    when(module.getProject()).thenReturn(project);

    assertThat(exclusions.canAnalyze(testFile, module).isExcluded()).isTrue();
  }

  @Test
  public void should_not_analyze_if_file_is_invalid() {
    VirtualFile f = mock(VirtualFile.class);
    when(f.isValid()).thenReturn(false);

    assertThat(exclusions.canAnalyze(f, module).isExcluded()).isTrue();
  }
}
