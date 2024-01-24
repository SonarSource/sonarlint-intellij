/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BuildFinishedAnalysisTriggerTests extends AbstractSonarLintLightTests {
  private AnalysisSubmitter submitter = mock(AnalysisSubmitter.class);

  private BuildFinishedAnalysisTrigger trigger;

  @BeforeEach
  void prepare() {
    replaceProjectService(AnalysisSubmitter.class, submitter);
    trigger = new BuildFinishedAnalysisTrigger();
  }

  @Test
  void should_trigger_automake() {
    trigger.buildFinished(getProject(), UUID.randomUUID(), true);
    verify(submitter).autoAnalyzeOpenFiles(TriggerType.COMPILATION);
  }

  @Test
  void should_not_trigger_if_not_automake() {
    trigger.buildFinished(getProject(), UUID.randomUUID(), false);
    verifyNoInteractions(submitter);
  }

  @Test
  void other_events_should_be_noop() {
    trigger.buildStarted(getProject(), UUID.randomUUID(), true);
    verifyNoInteractions(submitter);
  }
}
