/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.filters.FilterSecurityHotspotAction;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterSecurityHotspotActionGroupTests extends AbstractSonarLintLightTests {

  private FilterSecurityHotspotActionGroup actionGroup;
  private SonarLintToolWindow toolWindow;

  @BeforeEach
  void prepare() {
    toolWindow = mock(SonarLintToolWindow.class);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);
    actionGroup = new FilterSecurityHotspotActionGroup("title", "description", null);
  }

  @Test
  void testSelectingShowAllFilter() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterSecurityHotspotAction action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[0];
    action.setSelected(event, true);

    // Show All is already selected by default, it shouldn't trigger a new filtering
    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.SHOW_ALL);
    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.LOCAL_ONLY);
    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.EXISTING_ON_SERVER);
  }

  @Test
  void testSelectingLocalOnlyFilter() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterSecurityHotspotAction action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[1];
    action.setSelected(event, true);

    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.SHOW_ALL);
    verify(toolWindow).filterSecurityHotspotTab(SecurityHotspotFilters.LOCAL_ONLY);
    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.EXISTING_ON_SERVER);
  }

  @Test
  void testSelectingExistingOnSonarQubeFilter() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterSecurityHotspotAction action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[2];
    action.setSelected(event, true);

    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.SHOW_ALL);
    verify(toolWindow, never()).filterSecurityHotspotTab(SecurityHotspotFilters.LOCAL_ONLY);
    verify(toolWindow).filterSecurityHotspotTab(SecurityHotspotFilters.EXISTING_ON_SERVER);
  }

  @Test
  void testIsSelected() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    FilterSecurityHotspotAction action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[0];
    boolean isShowAllSelected = action.isSelected(event);
    action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[1];
    boolean isLocalOnlySelected = action.isSelected(event);
    action = (FilterSecurityHotspotAction) actionGroup.getChildren(event)[2];
    boolean isSonarQubeSelected = action.isSelected(event);

    assertThat(isShowAllSelected).isTrue();
    assertThat(isLocalOnlySelected).isFalse();
    assertThat(isSonarQubeSelected).isFalse();
  }

}
