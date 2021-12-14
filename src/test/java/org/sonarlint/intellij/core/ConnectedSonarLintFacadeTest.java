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
package org.sonarlint.intellij.core;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConnectedSonarLintFacadeTest extends AbstractSonarLintLightTests {

  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private ConnectedSonarLintFacade facade;

  @Before
  public void before() {
    facade = new ConnectedSonarLintFacade(engine, getProject(), "projectKey");
  }

  @Test
  public void should_get_rule_description() throws Exception {
    bindProject("projectKey");
    when(engine.getActiveRuleDetails(any(), any(), eq("rule1"), eq("projectKey"))).thenReturn(CompletableFuture.completedFuture(ruleDetails("rule1")));

    var ruleDescription = facade.getActiveRuleDescription("rule1").get();

    assertThat(ruleDescription.getKey()).isEqualTo("rule1");
    assertThat(ruleDescription.getHtml()).isEqualTo("<h2>ruleName</h2><table><tr>" +
      "<td><img valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\" src=\"file:///type/CODE_SMELL\"/></td><td class=\"pad\"><b>Code smell</b></td>" +
      "<td><img valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\" src=\"file:///severity/BLOCKER\"/></td>" +
      "<td class=\"pad\"><b>Blocker</b></td><td><b>rule1</b></td></tr></table>" +
      "<br />ruleHtmlDescription<br/><br/>ruleExtendedDescription");
  }

  @Test
  public void should_start_analysis() {
    var results = mock(AnalysisResults.class);
    var configCaptor = ArgumentCaptor.forClass(ConnectedAnalysisConfiguration.class);
    when(engine.analyze(configCaptor.capture(), any(IssueListener.class), any(ClientLogOutput.class), any(ClientProgressMonitor.class))).thenReturn(results);
    assertThat(facade.startAnalysis(getModule(), Collections.emptyList(), mock(IssueListener.class), Collections.emptyMap(), mock(ClientProgressMonitor.class))).isEqualTo(results);
    var config = configCaptor.getValue();
    assertThat(config.projectKey()).isEqualTo("projectKey");
  }

  private void bindProject(String projectKey) {
    var connection = ServerConnection.newBuilder().setName("connectionName").setHostUrl("http://localhost:9000").build();
    getGlobalSettings().addServerConnection(connection);
    getProjectSettings().bindTo(connection, projectKey);
  }

  private static ConnectedRuleDetails ruleDetails(String ruleKey) {
    return new ConnectedRuleDetails() {
      @Override
      public String getExtendedDescription() {
        return "ruleExtendedDescription";
      }

      @Override
      public String getKey() {
        return ruleKey;
      }

      @Override
      public String getName() {
        return "ruleName";
      }

      @Override
      public String getHtmlDescription() {
        return "ruleHtmlDescription";
      }

      @Override
      public Language getLanguage() {
        return Language.JAVA;
      }

      @Override
      public String getSeverity() {
        return "BLOCKER";
      }

      @Override
      public String getType() {
        return "CODE_SMELL";
      }
    };
  }
}
