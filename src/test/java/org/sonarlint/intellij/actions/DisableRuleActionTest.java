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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DisableRuleActionTest extends AbstractSonarLintLightTests {
  private static final String RULE_KEY = "key";
  private DisableRuleAction action = new DisableRuleAction();
  private AnActionEvent event = mock(AnActionEvent.class);
  private LiveIssue issue = mock(LiveIssue.class);
  private Presentation presentation = new Presentation();

  @Before
  public void start() {
    when(event.getProject()).thenReturn(getProject());
    when(event.getPresentation()).thenReturn(presentation);
  }

  @Test
  public void should_be_enabled_when_issue_present() {
    when(event.getData(DisableRuleAction.ISSUE_DATA_KEY)).thenReturn(issue);

    action.update(event);

    assertThat(presentation.isEnabled()).isTrue();
    assertThat(presentation.isVisible()).isTrue();
  }

  @Test
  public void should_be_not_visible_when_issue_not_present() {
    action.update(event);

    assertThat(presentation.isEnabled()).isFalse();
    assertThat(presentation.isVisible()).isFalse();
  }

  @Test
  public void should_be_not_visible_when_bound() {
    when(event.getData(DisableRuleAction.ISSUE_DATA_KEY)).thenReturn(issue);
    getProjectSettings().setBindingEnabled(true);
    action.update(event);

    assertThat(presentation.isEnabled()).isFalse();
    assertThat(presentation.isVisible()).isFalse();
  }

  @Test
  public void should_be_disabled_if_rule_is_excluded() {
    when(event.getData(DisableRuleAction.ISSUE_DATA_KEY)).thenReturn(issue);
    when(issue.getRuleKey()).thenReturn(RULE_KEY);
    getGlobalSettings().disableRule(RULE_KEY);
    action.update(event);

    assertThat(presentation.isEnabled()).isFalse();
    assertThat(presentation.isVisible()).isTrue();
  }

  @Test
  public void should_be_disabled_if_project_is_null() {
    when(event.getProject()).thenReturn(null);
    action.update(event);

    assertThat(presentation.isEnabled()).isFalse();
    assertThat(presentation.isVisible()).isFalse();
  }

// XXX how should we check that ? Should we also check that an analysis is triggered ?
//  @Test
//  public void no_op_if_project_is_null() {
//    when(event.getProject()).thenReturn(null);
//    action.update(event);
//    verifyZeroInteractions(submitter);
//  }

  @Test
  public void should_disable_rule() {
    when(event.getData(DisableRuleAction.ISSUE_DATA_KEY)).thenReturn(issue);
    when(issue.getRuleKey()).thenReturn(RULE_KEY);

    action.actionPerformed(event);

    assertThat(getGlobalSettings().excludedRules()).containsExactly(RULE_KEY);
  }

}
