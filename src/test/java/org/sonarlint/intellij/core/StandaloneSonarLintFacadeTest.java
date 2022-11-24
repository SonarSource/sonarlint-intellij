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

import java.util.Collections;
import java.util.List;
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
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandaloneSonarLintFacadeTest extends AbstractSonarLintLightTests {
  private StandaloneSonarLintEngine engine;
  private StandaloneSonarLintFacade facade;

  @Before
  public void before() {
    engine = mock(StandaloneSonarLintEngine.class);
    facade = new StandaloneSonarLintFacade(getProject(), engine);
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
    StandaloneRuleParam aParam = aParam();
    when(rule.paramDetails()).thenReturn(List.of(aParam));
    when(rule.getKey()).thenReturn(ruleKey);
    when(rule.getName()).thenReturn("ruleName");
    when(rule.getHtmlDescription()).thenReturn("ruleDescription");
    when(rule.getLanguage()).thenReturn(Language.JAVA);
    when(rule.getDefaultSeverity()).thenReturn(IssueSeverity.MAJOR);
    when(rule.getType()).thenReturn(RuleType.BUG);
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
