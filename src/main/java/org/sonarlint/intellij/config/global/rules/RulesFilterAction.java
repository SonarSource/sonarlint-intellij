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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

class RulesFilterAction extends DefaultActionGroup implements Toggleable, DumbAware {
  private final RulesFilterModel model;

  RulesFilterAction(RulesFilterModel model) {
    super("Filter Rules", true);
    this.model = model;
    getTemplatePresentation().setIcon(AllIcons.General.Filter);
    add(new ResetFilterAction());
    addSeparator();
    add(new FilterCheckboxAction("Show Only Enabled", model::setShowOnlyEnabled, model::isShowOnlyEnabled));
    add(new FilterCheckboxAction("Show Only Disabled", model::setShowOnlyDisabled, model::isShowOnlyDisabled));
    add(new FilterCheckboxAction("Show Only Changed", model::setShowOnlyChanged, model::isShowOnlyChanged));
  }

  private static class FilterCheckboxAction extends CheckboxAction implements DumbAware {
    private final Consumer<Boolean> setter;
    private final Supplier<Boolean> getter;

    FilterCheckboxAction(String label, Consumer<Boolean> setter, Supplier<Boolean> getter) {
      super(label);
      this.setter = setter;
      this.getter = getter;
    }

    @Override public boolean isSelected(AnActionEvent e) {
      return getter.get();
    }

    @Override public void setSelected(AnActionEvent e, boolean state) {
      setter.accept(state);
    }
  }

  private class ResetFilterAction extends DumbAwareAction {
    ResetFilterAction() {
      super("Reset Filter");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      model.reset(true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(!model.isEmpty());
    }
  }
}
