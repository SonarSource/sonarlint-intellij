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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.Action;
import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeAnalyzersDialog extends DialogWrapper {
  private final Project project;

  public CodeAnalyzersDialog(Project project) {
    super(project);
    super.setTitle("SonarLint Additional Information");
    this.project = project;
    super.init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return new SonarLintProjectAnalyzersPanel(project);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] {getOKAction()};
  }
}
