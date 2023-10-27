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
package org.sonarlint.intellij.core;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectedSonarLintFacadeTests extends AbstractSonarLintLightTests {

  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private ConnectedSonarLintFacade facade;

  @BeforeEach
  void before() {
    facade = new ConnectedSonarLintFacade(engine, getProject(), "projectKey");
  }

  @Test
  void should_start_analysis() {
    var results = mock(AnalysisResults.class);
    var configCaptor = ArgumentCaptor.forClass(ConnectedAnalysisConfiguration.class);
    when(engine.analyze(configCaptor.capture(), any(IssueListener.class), any(ClientLogOutput.class), any(ClientProgressMonitor.class))).thenReturn(results);
    assertThat(facade.startAnalysis(getModule(), Collections.emptyList(), mock(IssueListener.class), Collections.emptyMap(), mock(ClientProgressMonitor.class))).isEqualTo(results);
    var config = configCaptor.getValue();
    assertThat(config.getProjectKey()).isEqualTo("projectKey");
  }
}
