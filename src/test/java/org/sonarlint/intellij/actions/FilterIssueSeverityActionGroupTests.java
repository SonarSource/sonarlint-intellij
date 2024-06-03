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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.filters.FilterIssueSeverityAction;
import org.sonarlint.intellij.actions.filters.IssueSeverityFilters;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterIssueSeverityActionGroupTests extends AbstractSonarLintLightTests {

  private FilterIssueSeverityActionGroup actionGroup;
  private SonarLintToolWindow toolWindow;

  @BeforeEach
  void prepare() {
    toolWindow = mock(SonarLintToolWindow.class);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);
    actionGroup = new FilterIssueSeverityActionGroup("title", "description", null);
  }

  @ParameterizedTest
  @EnumSource(IssueSeverityFilters.class)
  void testSelectingShowAllFilter(IssueSeverityFilters filter) {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterIssueSeverityAction action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[0];
    action.setSelected(event, true);

    // Show All is already selected by default, it shouldn't trigger a new filtering
    verify(toolWindow, never()).filterCurrentFileTab(filter);
    verify(toolWindow, never()).filterReportTab(filter);
  }

  @Test
  void testSelectingFilter() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterIssueSeverityAction action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[1];
    action.setSelected(event, true);

    verify(toolWindow, never()).filterCurrentFileTab(IssueSeverityFilters.SHOW_ALL);
    verify(toolWindow).filterCurrentFileTab(IssueSeverityFilters.INFO);
    verify(toolWindow, never()).filterCurrentFileTab(IssueSeverityFilters.MINOR);
    verify(toolWindow, never()).filterCurrentFileTab(IssueSeverityFilters.MAJOR);
    verify(toolWindow, never()).filterCurrentFileTab(IssueSeverityFilters.CRITICAL);
    verify(toolWindow, never()).filterCurrentFileTab(IssueSeverityFilters.BLOCKER);

    verify(toolWindow, never()).filterReportTab(IssueSeverityFilters.SHOW_ALL);
    verify(toolWindow).filterReportTab(IssueSeverityFilters.INFO);
    verify(toolWindow, never()).filterReportTab(IssueSeverityFilters.MINOR);
    verify(toolWindow, never()).filterReportTab(IssueSeverityFilters.MAJOR);
    verify(toolWindow, never()).filterReportTab(IssueSeverityFilters.CRITICAL);
    verify(toolWindow, never()).filterReportTab(IssueSeverityFilters.BLOCKER);
  }

  @Test
  void testIsSelected() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterIssueSeverityAction action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[0];
    boolean isShowAllSelected = action.isSelected(event);
    action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[1];
    boolean isInfoSelected = action.isSelected(event);
    action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[2];
    boolean isMinorSelected = action.isSelected(event);
    action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[3];
    boolean isMajorSelected = action.isSelected(event);
    action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[4];
    boolean isCriticalSelected = action.isSelected(event);
    action = (FilterIssueSeverityAction) actionGroup.getChildren(event)[5];
    boolean isBlockerSelected = action.isSelected(event);

    assertThat(isShowAllSelected).isTrue();
    assertThat(isInfoSelected).isFalse();
    assertThat(isMinorSelected).isFalse();
    assertThat(isMajorSelected).isFalse();
    assertThat(isCriticalSelected).isFalse();
    assertThat(isBlockerSelected).isFalse();
  }

}
