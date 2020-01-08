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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static com.intellij.openapi.actionSystem.Toggleable.SELECTED_PROPERTY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RulesFilterActionTest extends SonarTest {
  private ActionManager actionManager = register(app, ActionManager.class);
  private RulesFilterModel model = mock(RulesFilterModel.class);
  private AnActionEvent event = mock(AnActionEvent.class);
  private Presentation presentation = new Presentation();
  private RulesFilterAction action;

  @Before
  public void prepare() {
    when(event.getPresentation()).thenReturn(presentation);
    action = new RulesFilterAction(model);
  }

  @Test
  public void show_only_changed() {
    AnAction changed = findAction("Changed");

    presentation.putClientProperty(SELECTED_PROPERTY, true);

    changed.actionPerformed(event);
    verify(model).setShowOnlyChanged(true);
    verify(model).isShowOnlyChanged();
    verifyNoMoreInteractions(model);
  }

  @Test
  public void show_only_disabled() {
    AnAction disabled = findAction("Disabled");

    presentation.putClientProperty(SELECTED_PROPERTY, true);

    disabled.actionPerformed(event);
    verify(model).setShowOnlyDisabled(true);
    verify(model).isShowOnlyDisabled();
    verifyNoMoreInteractions(model);
  }

  @Test
  public void show_only_enabled() {
    AnAction enabled = findAction("Enabled");

    presentation.putClientProperty(SELECTED_PROPERTY, true);

    enabled.actionPerformed(event);
    verify(model).setShowOnlyEnabled(true);
    verify(model).isShowOnlyEnabled();
    verifyNoMoreInteractions(model);
  }

  @Test
  public void reset_filter() {
    AnAction reset = findAction("Filter");
    reset.actionPerformed(event);
    verify(model).reset(true);

    reset.update(event);
    verify(model).isEmpty();
    verifyNoMoreInteractions(model);
  }

  private AnAction findAction(String text) {
    return Arrays.stream(action.getChildActionsOrStubs())
      .filter(a -> a.getTemplatePresentation().getText() != null && a.getTemplatePresentation().getText().contains(text))
      .findFirst()
      .get();
  }
}
