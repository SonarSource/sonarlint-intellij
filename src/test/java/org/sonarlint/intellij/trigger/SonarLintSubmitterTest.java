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
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintSubmitterTest {
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

  private SonarLintGlobalSettings globalSettings;

  private SonarLintSubmitter submitter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    globalSettings = new SonarLintGlobalSettings();
    globalSettings.setAutoTrigger(true);
    submitter = new SonarLintSubmitter(project, console, fileEditorManager, telemetry, sonarLintJobManager, globalSettings, utils, exclusions);
  }

  @Test
  public void should_submit_open_files() {
    VirtualFile f1 = mock(VirtualFile.class);
    Module m1 = mock(Module.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(m1);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, m1)).thenReturn(LocalFileExclusions.Result.notExcluded());
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(Collections.singletonMap(m1, Collections.singleton(f1))), eq(TriggerType.BINDING_CHANGE), eq(null));
  }

  @Test
  public void should_submit_manual() {
    VirtualFile f1 = mock(VirtualFile.class);
    Module m1 = mock(Module.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(m1);
    when(exclusions.shouldAnalyze(f1, m1)).thenReturn(true);

    submitter.submitFilesModal(Collections.singleton(f1), TriggerType.BINDING_CHANGE);
    verify(sonarLintJobManager).submitManual(eq(Collections.singletonMap(m1, Collections.singleton(f1))), eq(TriggerType.BINDING_CHANGE), eq(true), eq(null));
  }

  @Test
  public void should_not_submit_if_fail_checks() {
    VirtualFile f1 = mock(VirtualFile.class);
    Module m1 = mock(Module.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(m1);
    when(exclusions.checkExclusionAutomaticAnalysis(f1, m1)).thenReturn(LocalFileExclusions.Result.excluded(""));
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
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
    Module m1 = mock(Module.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(m1);
    when(exclusions.shouldAnalyze(f1, m1)).thenReturn(false);
    submitter.submitFiles(Collections.singleton(f1), TriggerType.BINDING_CHANGE, false);

    verifyZeroInteractions(sonarLintJobManager);
  }

  @Test
  public void should_not_submit_if_no_module() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.findModuleForFile(f1, project)).thenReturn(null);
    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {f1});

    submitter.submitOpenFilesAuto(TriggerType.BINDING_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }
}
