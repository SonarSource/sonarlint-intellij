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

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.exception.InvalidBindingException;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class LocalFileExclusionsTest extends AbstractSonarLintLightTests {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private LocalFileExclusions underTest;

  Map<VirtualFile, ExcludeResult> excludeReasons = new HashMap<>();

  @Before
  public void prepare() {
    underTest = new LocalFileExclusions(getProject());
  }

  @Test
  public void should_not_exclude_source_file() throws InvalidBindingException {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), false, excludeReasons::put);
    assertIsNotExcluded(file, nonExcludedFilesByModule);
  }

  @Test
  public void should_exclude_if_file_not_part_of_a_module() throws Exception {
    VirtualFile tempFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp.newFile());

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(tempFile), false, excludeReasons::put);
    assertIsExcluded(tempFile, nonExcludedFilesByModule, "file is not part of any module in IntelliJ's project structure");
  }

  @Test
  public void should_exclude_if_file_is_binary() throws InvalidBindingException {
    VirtualFile file = myFixture.copyFileToProject("foo.bin", "foo.bin");

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), false, excludeReasons::put);
    assertIsExcluded(file, nonExcludedFilesByModule, "file's type or location are not supported");
  }

  @Test
  public void should_exclude_if_file_is_deleted() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    WriteAction.runAndWait(() -> file.delete(null));

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), false, excludeReasons::put);
    assertIsExcluded(file, nonExcludedFilesByModule, "file is not part of any module in IntelliJ's project structure");
  }

  @Test
  public void should_exclude_if_file_excluded_in_project_config() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    setProjectLevelExclusions(singletonList("GLOB:foo.php"));

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), false, excludeReasons::put);
    assertIsExcluded(file, nonExcludedFilesByModule, "file matches exclusions defined in the SonarLint Project Settings");
  }

  @Test
  public void should_not_exclude_if_file_excluded_in_project_config_when_forced_analysis() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    setProjectLevelExclusions(singletonList("GLOB:foo.php"));

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), true, excludeReasons::put);
    assertIsNotExcluded(file, nonExcludedFilesByModule);
  }

  @Test
  public void should_exclude_if_file_excluded_in_global_config() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    setGlobalLevelExclusions(singletonList("foo.php"));

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), false, excludeReasons::put);
    assertIsExcluded(file, nonExcludedFilesByModule, "file matches exclusions defined in the SonarLint Global Settings");
  }

  @Test
  public void should_not_exclude_if_file_excluded_in_global_config_when_forced_analysis() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    setGlobalLevelExclusions(singletonList("GLOB:foo.php"));

    Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), true, excludeReasons::put);
    assertIsNotExcluded(file, nonExcludedFilesByModule);
  }

  @Test
  public void should_exclude_if_power_save_mode() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    try {
      PowerSaveMode.setEnabled(true);

      Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule = underTest.retainNonExcludedFilesByModules(singletonList(file), true, excludeReasons::put);
      assertIsExcluded(file, nonExcludedFilesByModule, "power save mode is enabled");
    } finally {
      PowerSaveMode.setEnabled(false);
    }
  }

  private void assertIsNotExcluded(VirtualFile file, Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule) {
    assertThat(excludeReasons).isEmpty();
    assertThat(nonExcludedFilesByModule.get(getModule())).containsExactly(file);
  }

  private void assertIsExcluded(VirtualFile file, Map<Module, Collection<VirtualFile>> nonExcludedFilesByModule, String s) {
    assertThat(excludeReasons).containsOnlyKeys(file);
    assertThat(excludeReasons.get(file).isExcluded()).isTrue();
    assertThat(excludeReasons.get(file).excludeReason()).isEqualTo(s);
    assertThat(nonExcludedFilesByModule).isEmpty();
  }

}
