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
package org.sonarlint.intellij.editor;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.util.SonarLintUtils;

public class EscapeHandler extends EditorActionHandler {

  private final EditorActionHandler originalHandler;

  public EscapeHandler(EditorActionHandler originalHandler) {
    super(false);
    this.originalHandler = originalHandler;
  }

  @Override
  protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    var project = editor.getProject();
    if (project != null) {
      var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
      if (highlighting.isActiveInEditor(editor)) {
        highlighting.removeHighlights();
        return;
      }
    }
    originalHandler.execute(editor, caret, dataContext);
  }

  @Override
  public boolean isEnabledForCaret(Editor editor, Caret caret, DataContext ctx) {
    return isActive(editor) || originalHandler.isEnabled(editor, caret, ctx);
  }

  private static boolean isActive(Editor editor) {
    var project = editor.getProject();
    if (project != null) {
      var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
      return highlighting.isActiveInEditor(editor);
    }
    return false;
  }

}
