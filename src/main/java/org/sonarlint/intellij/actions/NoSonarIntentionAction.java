/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.util.ResourceLoader;

import javax.swing.Icon;
import java.io.IOException;
import java.util.Collections;

/**
 *  * Implement {@link com.intellij.openapi.util.Iconable Iconable} interface to
 * change icon in quick fix popup menu.
 */
public class NoSonarIntentionAction extends BaseIntentionAction implements Iconable {
  private static final Logger LOGGER = Logger.getInstance(NoSonarIntentionAction.class);
  private static final String NOSONAR_COMMENT = "// NOSONAR";

  private final RangeMarker range;

  public NoSonarIntentionAction(RangeMarker range) {
    this.range = range;
  }

  @Nls @NotNull @Override public String getFamilyName() {
    return "family name";
  }

  @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    // how can we disable if NOSONAR is already in the line?
    // some issues concern entire file and have no range (no action possible)
    return editor.getDocument().isWritable() && range != null;
  }

  @NotNull @Override public String getText() {
    return "Add 'NOSONAR'";
  }

  @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    Document doc = editor.getDocument();

    // issue can span through multiple lines, so we use the range start offset and not the caret position (editor.getCaretModel().getOffset())
    int lineNum = doc.getLineNumber(range.getStartOffset());
    int lineEndOffset = doc.getLineEndOffset(lineNum);

    // maybe add a PsiComment to the AST with PsiElementFactory, instead? It would require us to position it in the Psi tree.
    doc.insertString(lineEndOffset, " " + NOSONAR_COMMENT);

    Module module = ModuleUtil.findModuleForFile(file.getVirtualFile(), project);
    project.getComponent(SonarLintAnalyzer.class).submitAsync(module, Collections.singleton(file.getVirtualFile()));
  }

  @Override public boolean startInWriteAction() {
    return true;
  }

  @Override public Icon getIcon(@IconFlags int flags) {
    try {
      return ResourceLoader.getIcon("onde-sonar-16.png");
    } catch (IOException e) {
      LOGGER.error("Couldn't load action icon", e);
    }
    return null;
  }
}
