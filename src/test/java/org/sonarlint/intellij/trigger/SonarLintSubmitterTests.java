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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.trigger.SonarLintSubmitter.NO_OP_CALLBACK;

public class SonarLintSubmitterTests extends AbstractSonarLintLightTests {
  private SonarLintJobManager sonarLintJobManager = mock(SonarLintJobManager.class);
  private ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private SonarLintFacade facade = mock(SonarLintFacade.class);

  private SonarLintSubmitter submitter;

  @Before
  public void start() throws InvalidBindingException {
    when(bindingManager.getFacade()).thenReturn(facade);
    when(facade.getExcluded(any(Module.class), anyCollection(), any(Predicate.class))).thenReturn(Collections.emptySet());
    getGlobalSettings().setAutoTrigger(true);
    submitter = new SonarLintSubmitter(getProject());
    replaceProjectService(SonarLintJobManager.class, sonarLintJobManager);
    replaceProjectService(ProjectBindingManager.class, bindingManager);
  }

  @Test
  public void should_submit_open_files() {
    VirtualFile f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    FileEditorManager.getInstance(getProject()).openFile(f1, false);

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(singletonMap(getModule(), singleton(f1))), eq(emptyList()), eq(TriggerType.CONFIG_CHANGE), eq(NO_OP_CALLBACK));
  }

  @Test
  public void should_submit_manual() {
    VirtualFile f1 = myFixture.copyFileToProject("foo.php", "foo.php");

    final AnalysisCallback analysisCallback = mock(AnalysisCallback.class);
    submitter.submitFilesModal(singleton(f1), TriggerType.CONFIG_CHANGE, analysisCallback);
    verify(sonarLintJobManager).submitManual(eq(singletonMap(getModule(), singleton(f1))), eq(emptyList()), eq(TriggerType.CONFIG_CHANGE), eq(true), eq(analysisCallback));
  }

  @Test
  public void should_clear_issues_if_excluded() {
    VirtualFile f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    FileEditorManager.getInstance(getProject()).openFile(f1, false);

    setProjectLevelExclusions(singletonList("GLOB:foo.php"));

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(sonarLintJobManager).submitBackground(eq(emptyMap()), eq(singletonList(f1)), eq(TriggerType.CONFIG_CHANGE), eq(NO_OP_CALLBACK));
  }

  @Test
  public void should_not_submit_if_auto_disable() {
    getGlobalSettings().setAutoTrigger(false);
    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verifyZeroInteractions(sonarLintJobManager);
  }

}
