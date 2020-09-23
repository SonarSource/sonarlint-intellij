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
package org.sonarlint.intellij.core;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConnectedSonarLintFacadeTest extends AbstractSonarLintLightTests {

  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private ConnectedSonarLintFacade facade;

  @Before
  public void before() {
    getProjectSettings().setProjectKey("projectKey");
    facade = new ConnectedSonarLintFacade("mySqServer", engine, getProject());
  }

  @Test
  public void should_get_rule_name() {
    ConnectedRuleDetails ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getName()).thenReturn("name");
    when(engine.getActiveRuleDetails("rule1", "projectKey")).thenReturn(ruleDetails);
    assertThat(facade.getRuleName("rule1")).isEqualTo("name");
    assertThat(facade.getRuleName("invalid")).isNull();
  }

  @Test
  public void should_get_rule_details() {
    ConnectedRuleDetails ruleDetails = mock(ConnectedRuleDetails.class);
    when(engine.getActiveRuleDetails("rule1", "projectKey")).thenReturn(ruleDetails);
    assertThat(facade.getActiveRuleDetails("rule1")).isEqualTo(ruleDetails);
  }

  @Test
  public void should_get_description() {
    ConnectedRuleDetails ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getExtendedDescription()).thenReturn("desc");
    when(ruleDetails.getHtmlDescription()).thenReturn("html");
    when(engine.getActiveRuleDetails("rule1", "projectKey")).thenReturn(ruleDetails);
    assertThat(facade.getDescription("rule1")).isEqualTo("html<br/><br/>desc");
    assertThat(facade.getDescription("invalid")).isNull();
  }

  @Test
  public void should_start_analysis() {
    String projectKey = "project-key";
    getProjectSettings().setProjectKey(projectKey);
    AnalysisResults results = mock(AnalysisResults.class);
    ArgumentCaptor<ConnectedAnalysisConfiguration> configCaptor = ArgumentCaptor.forClass(ConnectedAnalysisConfiguration.class);
    when(engine.analyze(configCaptor.capture(), any(IssueListener.class), any(LogOutput.class), any(ProgressMonitor.class))).thenReturn(results);
    assertThat(facade.startAnalysis(Collections.emptyList(), mock(IssueListener.class), Collections.emptyMap(), mock(ProgressMonitor.class))).isEqualTo(results);
    ConnectedAnalysisConfiguration config = configCaptor.getValue();
    assertThat(config.projectKey()).isEqualTo(projectKey);
  }
}
