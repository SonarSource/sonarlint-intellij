/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;

import static java.util.Collections.singleton;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.trigger.SonarLintSubmitter.NO_OP_CALLBACK;

public class SonarLintSubmitterTests extends AbstractSonarLintLightTests {
  private AnalysisManager analysisManager = mock(AnalysisManager.class);
  private ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private SonarLintFacade facade = mock(SonarLintFacade.class);

  private SonarLintSubmitter submitter;

  @Before
  public void start() throws InvalidBindingException {
    when(bindingManager.getFacade(any())).thenReturn(facade);
    when(facade.getExcluded(any(Module.class), anyCollection(), any(Predicate.class))).thenReturn(Collections.emptySet());
    getGlobalSettings().setAutoTrigger(true);
    submitter = new SonarLintSubmitter(getProject());
    replaceProjectService(AnalysisManager.class, analysisManager);
    replaceProjectService(ProjectBindingManager.class, bindingManager);
  }

  @Test
  public void should_submit_open_files() {
    var f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    FileEditorManager.getInstance(getProject()).openFile(f1, false);

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(analysisManager).submitBackground(List.of(f1), TriggerType.CONFIG_CHANGE, NO_OP_CALLBACK);
  }

  @Test
  public void should_submit_manual() {
    var f1 = myFixture.copyFileToProject("foo.php", "foo.php");

    final AnalysisCallback analysisCallback = mock(AnalysisCallback.class);
    submitter.submitFilesModal(singleton(f1), TriggerType.CONFIG_CHANGE, analysisCallback);
    verify(analysisManager).submitManual(singleton(f1), TriggerType.CONFIG_CHANGE, true, analysisCallback);
  }

  @Test
  public void should_submit_open_files_auto() {
    var f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    FileEditorManager.getInstance(getProject()).openFile(f1, false);

    setProjectLevelExclusions(List.of("GLOB:foo.php"));

    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verify(analysisManager).submitBackground(List.of(f1), TriggerType.CONFIG_CHANGE, NO_OP_CALLBACK);
  }

  @Test
  public void should_not_submit_if_auto_disable() {
    getGlobalSettings().setAutoTrigger(false);
    submitter.submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    verifyNoInteractions(analysisManager);
  }

}
