/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParamType;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandaloneSonarLintFacadeTest extends AbstractSonarLintLightTests {
  private final StandaloneSonarLintEngine engine = mock(StandaloneSonarLintEngine.class);
  private StandaloneSonarLintFacade facade;

  @Before
  public void before() {
    facade = new StandaloneSonarLintFacade(getProject(), engine);
  }

  @Test
  public void should_get_rule_description() throws Exception {
    when(engine.getRuleDetails("rule1")).thenReturn(Optional.of(ruleDetails("rule1")));

    var ruleDescription = facade.getActiveRuleDescription("rule1").get();

    assertThat(ruleDescription.getKey()).isEqualTo("rule1");
    assertThat(ruleDescription.getHtml()).isEqualTo("<h2>ruleName</h2><table>" +
      "<tr><td><img valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\" src=\"file:///type/BUG\"/></td>" +
      "<td class=\"pad\"><b>Bug</b></td>" +
      "<td><img valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\" src=\"file:///severity/MAJOR\"/></td>" +
      "<td class=\"pad\"><b>Major</b></td>" +
      "<td><b>rule1</b></td></tr></table>" +
      "<br />ruleDescription" +
      "<table class=\"rule-params\">" +
      "<caption><h2>Parameters</h2></caption>" +
      "<tr class='thead'><td colspan=\"2\">Following parameter values can be set in <a href=\"#rule\">Rule Settings</a>. In connected mode, server side configuration overrides local settings.</td></tr>"
      +
      "<tr class='tbody'><th>paramName<br/><br/></th><td><p>paramDescription</p><p><small>Current value: <code>paramDefaultValue</code></small></p><p><small>Default value: <code>paramDefaultValue</code></small></p></td></tr>"
      +
      "</table>");
  }

  @Test
  public void should_start_analysis() {
    var results = mock(AnalysisResults.class);
    when(engine.analyze(any(StandaloneAnalysisConfiguration.class), any(IssueListener.class), any(ClientLogOutput.class), any(ClientProgressMonitor.class))).thenReturn(results);
    assertThat(facade.startAnalysis(getModule(), Collections.emptyList(), mock(IssueListener.class), Collections.emptyMap(), mock(ClientProgressMonitor.class))).isEqualTo(results);
  }

  private static StandaloneRuleDetails ruleDetails(String ruleKey) {
    var rule = mock(StandaloneRuleDetails.class);
    when(rule.isActiveByDefault()).thenReturn(true);
    when(rule.getTags()).thenReturn(new String[] {"tag"});
    when(rule.paramDetails()).thenReturn(List.of(aParam()));
    when(rule.getKey()).thenReturn(ruleKey);
    when(rule.getName()).thenReturn("ruleName");
    when(rule.getHtmlDescription()).thenReturn("ruleDescription");
    when(rule.getLanguage()).thenReturn(Language.JAVA);
    when(rule.getSeverity()).thenReturn("MAJOR");
    when(rule.getType()).thenReturn("BUG");
    return rule;
  }

  private static StandaloneRuleParam aParam() {
    var param = mock(StandaloneRuleParam.class);
    when(param.key()).thenReturn("paramKey");
    when(param.name()).thenReturn("paramName");
    when(param.description()).thenReturn("paramDescription");
    when(param.defaultValue()).thenReturn("paramDefaultValue");
    when(param.type()).thenReturn(StandaloneRuleParamType.STRING);
    when(param.multiple()).thenReturn(false);
    when(param.possibleValues()).thenReturn(List.of("YES", "NO", "MAYBE"));
    return param;
  }
}
