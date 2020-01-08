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
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.ui.CodeAnalyzersDialog;

public class SonarShowCodeAnalyzers extends AbstractSonarAction {

  @Override protected boolean isEnabled(AnActionEvent e, Project project, SonarLintStatus status) {
    return e.getProject() != null;
  }

  @Override public void actionPerformed(AnActionEvent e) {
    if (e.getProject() != null) {
      new CodeAnalyzersDialog(e.getProject()).showAndGet();
    }
  }
}
