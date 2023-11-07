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
package org.sonarlint.intellij.core;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssueListener;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.MockitoKotlinHelperKt.eq;

class ConnectedSonarLintFacadeTests extends AbstractSonarLintLightTests {

  private SonarLintAnalysisEngine engine;
  private EngineFacade facade;

  @BeforeEach
  void before() {
    engine = mock(SonarLintAnalysisEngine.class);
    facade = new EngineFacade(getProject(), engine);
  }

  @Test
  void should_start_analysis() {
    var results = mock(AnalysisResults.class);
    var configCaptor = ArgumentCaptor.forClass(AnalysisConfiguration.class);
    when(engine.analyze(configCaptor.capture(), any(RawIssueListener.class), any(ClientLogOutput.class), any(ClientProgressMonitor.class), eq(BackendService.Companion.moduleId(getModule())))).thenReturn(results);
    assertThat(facade.startAnalysis(getModule(), Collections.emptyList(), Collections.emptyMap(), mock(RawIssueListener.class), mock(ClientProgressMonitor.class))).isEqualTo(results);
  }
}
