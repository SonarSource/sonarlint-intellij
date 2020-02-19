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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.VirtualFileTestPredicate;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintSubmitterTest extends SonarTest {
  private SonarLintConsole console = mock(SonarLintConsole.class);
  private FileEditorManager fileEditorManager = mock(FileEditorManager.class);
  private SonarLintJobManager sonarLintJobManager = mock(SonarLintJobManager.class);
  private SonarLintAppUtils utils = mock(SonarLintAppUtils.class);
  private Project project = mock(Project.class);
  private LocalFileExclusions exclusions = mock(LocalFileExclusions.class);
  private ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private Module module = mock(Module.class);
  private VirtualFileTestPredicate testPredicate = mock(VirtualFileTestPredicate.class);
  private SonarLintFacade facade = mock(SonarLintFacade.class);

  private SonarLintGlobalSettings globalSettings = new SonarLintGlobalSettings();
  private SonarLintSubmitter submitter;

  @Before
  public void start() throws InvalidBindingException {
    when(super.app.runReadAction(any(Computable.class))).thenAnswer(i -> ((Computable) i.getArgument(0)).compute());
    when(bindingManager.getFacade()).thenReturn(facade);
    when(facade.getExcluded(any(Module.class), anyCollection(), any(Predicate.class))).thenReturn(Collections.emptySet());
    globalSettings.setAutoTrigger(true);
    super.register(module, VirtualFileTestPredicate.class, testPredicate);
    submitter = new SonarLintSubmitter(project, console, fileEditorManager, sonarLintJobManager, globalSettings, utils,
      exclusions, bindingManager);
  }

  @Test
  public void should_submit_open_files() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusions(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(singletonMap(module, singleton(f1))), eq(emptyList()), eq(TriggerType.CONFIG_CHANGE), eq(null));
  }

  @Test
  public void should_submit_manual() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());

    submitter.submitFilesModal(singleton(f1), TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitManual(eq(singletonMap(module, singleton(f1))), eq(emptyList()), eq(TriggerType.CONFIG_CHANGE), eq(true), eq(null));
  }

  @Test
  public void should_clear_issues_if_excluded() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusions(f1, module)).thenReturn(LocalFileExclusions.Result.excluded(""));
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(emptyMap()), eq(singletonList(f1)), eq(TriggerType.CONFIG_CHANGE), eq(null));
  }

  @Test
  public void should_clear_issues_if_excluded_in_server() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusions(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(facade.getExcluded(any(Module.class), anyCollection(), any(Predicate.class))).thenReturn(singleton(f1));
    submitter.submitFiles(singleton(f1), TriggerType.CONFIG_CHANGE, false);

    verify(sonarLintJobManager).submitManual(eq(emptyMap()), eq(singletonList(f1)), eq(TriggerType.CONFIG_CHANGE), eq(false), eq(null));
  }

  @Test
  public void should_not_submit_if_auto_disable() {
    globalSettings.setAutoTrigger(false);
    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_clear_issues_if_not_analyzable() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.excluded(null));
    submitter.submitFiles(singleton(f1), TriggerType.ACTION, false);

    verify(sonarLintJobManager).submitManual(eq(emptyMap()), eq(singletonList(f1)), eq(TriggerType.ACTION), eq(false), eq(null));
  }

  @Test
  public void should_clear_issues_if_cant_analyze() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(null);
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});
    when(exclusions.checkExclusions(f1, null)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f1, null)).thenReturn(LocalFileExclusions.Result.excluded("Because"));

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(emptyMap()), eq(singletonList(f1)), eq(TriggerType.CONFIG_CHANGE), eq(null));
  }

  @Test
  public void should_not_crash_when_all_files_of_some_module_are_excluded() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusions(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());

    VirtualFile f2 = mock(VirtualFile.class);
    Module m2 = mock(Module.class);
    register(m2, VirtualFileTestPredicate.class, testPredicate);

    VirtualFile f3 = mock(VirtualFile.class);
    Module m3 = mock(Module.class);
    register(m3, VirtualFileTestPredicate.class, testPredicate);

    when(utils.findModuleForFile(f2, project)).thenReturn(m2);
    when(exclusions.checkExclusions(f2, m2)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f2, m2)).thenReturn(LocalFileExclusions.Result.notExcluded());

    when(utils.findModuleForFile(f3, project)).thenReturn(m3);
    when(exclusions.checkExclusions(f3, m3)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(exclusions.canAnalyze(f3, m3)).thenReturn(LocalFileExclusions.Result.notExcluded());

    when(facade.getExcluded(eq(module), any(), any())).thenReturn(singletonList(f1));
    when(facade.getExcluded(eq(m2), any(), any())).thenReturn(singletonList(f2));

    submitter.submitFiles(asList(f1, f2, f3), TriggerType.EDITOR_OPEN, true);
    verify(sonarLintJobManager).submitBackground(eq(singletonMap(m3, singleton(f3))), eq(asList(f1, f2)), eq(TriggerType.EDITOR_OPEN), eq(null));
  }
}
