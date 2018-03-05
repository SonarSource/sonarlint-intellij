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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.VirtualFileTestPredicate;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintSubmitterTest extends SonarTest {
  @Mock
  private SonarLintConsole console;
  @Mock
  private FileEditorManager fileEditorManager;
  @Mock
  private SonarLintJobManager sonarLintJobManager;
  @Mock
  private SonarLintAppUtils utils;
  @Mock
  private Project project;
  @Mock
  private SonarLintTelemetry telemetry;
  @Mock
  private LocalFileExclusions exclusions;
  @Mock
  private ProjectBindingManager bindingManager;
  @Mock
  private Module module;
  @Mock
  private VirtualFileTestPredicate testPredicate;
  @Mock
  private SonarLintFacade facade;

  private SonarLintGlobalSettings globalSettings;

  private SonarLintSubmitter submitter;

  @Before
  public void start() {
    MockitoAnnotations.initMocks(this);
    when(bindingManager.getFacade()).thenReturn(facade);
    when(facade.getExcluded(anyCollection(), any(Predicate.class))).thenReturn(Collections.emptySet());
    globalSettings = new SonarLintGlobalSettings();
    globalSettings.setAutoTrigger(true);
    super.register(module, VirtualFileTestPredicate.class, testPredicate);
    submitter = new SonarLintSubmitter(project, console, fileEditorManager, telemetry, sonarLintJobManager, globalSettings, utils,
      exclusions, bindingManager);
  }

  @Test
  public void should_submit_open_files() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(Collections.singletonMap(module, Collections.singleton(f1))), eq(TriggerType.BINDING_CHANGE), eq(null));
  }

  @Test
  public void should_submit_manual() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.canAnalyze(f1, module)).thenReturn(true);

    submitter.submitFilesModal(Collections.singleton(f1), TriggerType.BINDING_CHANGE);
    verify(sonarLintJobManager).submitManual(eq(Collections.singletonMap(module, Collections.singleton(f1))), eq(TriggerType.BINDING_CHANGE), eq(true), eq(null));
  }

  @Test
  public void should_not_submit_if_fail_checks() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, module)).thenReturn(LocalFileExclusions.Result.excluded(""));
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_submit_excluded_in_server() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(facade.getExcluded(anyCollection(), any(Predicate.class))).thenReturn(Collections.singleton(f1));
    submitter.submitFiles(Collections.singleton(f1), TriggerType.BINDING_CHANGE, false);
    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_submit_if_auto_disable() {
    globalSettings.setAutoTrigger(false);
    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_submit_if_not_analyzable() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.canAnalyze(f1, module)).thenReturn(false);
    submitter.submitFiles(Collections.singleton(f1), TriggerType.BINDING_CHANGE, false);

    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_submit_if_no_module() {
    when(exclusions.checkExclusionAutomaticAnalysis(any(VirtualFile.class), any(Module.class))).thenReturn(LocalFileExclusions.Result.notExcluded());
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(null);
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});
    when(exclusions.checkExclusionAutomaticAnalysis(f1, null)).thenReturn(LocalFileExclusions.Result.excluded(""));

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_crash_when_all_files_of_some_module_are_excluded() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(module);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, module)).thenReturn(LocalFileExclusions.Result.notExcluded());

    VirtualFile f2 = mock(VirtualFile.class);
    Module m2 = mock(Module.class);
    register(m2, VirtualFileTestPredicate.class, testPredicate);

    VirtualFile f3 = mock(VirtualFile.class);
    Module m3 = mock(Module.class);
    register(m3, VirtualFileTestPredicate.class, testPredicate);

    when(utils.findModuleForFile(f2, project)).thenReturn(m2);
    when(exclusions.checkExclusionAutomaticAnalysis(f2, m2)).thenReturn(LocalFileExclusions.Result.notExcluded());

    when(utils.findModuleForFile(f3, project)).thenReturn(m3);
    when(exclusions.checkExclusionAutomaticAnalysis(f3, m3)).thenReturn(LocalFileExclusions.Result.notExcluded());

    when(facade.getExcluded(any(), any())).thenReturn(Arrays.asList(f1, f2));

    submitter.submitFiles(Arrays.asList(f1, f2, f3), TriggerType.EDITOR_OPEN, true);
    verify(sonarLintJobManager).submitBackground(eq(Collections.singletonMap(m3, Collections.singleton(f3))), eq(TriggerType.EDITOR_OPEN), eq(null));
  }
}
