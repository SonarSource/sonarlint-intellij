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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarlint.intellij.util.SonarLintUtils;

public class IssueNode extends AbstractNode {
  private static final Logger LOGGER = Logger.getInstance(IssueNode.class);
  private final IssuePointer issue;

  public IssueNode(IssuePointer issue) {
    this.issue = issue;
  }

  @Override public void render(ColoredTreeCellRenderer renderer) {
    if(!issue.isValid()) {
      renderer.append("Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
      return;
    }

    String severity = issue.issue().getSeverity();

    if (severity != null) {
      try {
        renderer.setIcon(ResourceLoader.getSeverityIcon(issue.issue().getSeverity()));
      } catch (IOException e) {
        LOGGER.error("Couldn't load icon for severity: " + severity, e);
      }
    }
    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);

    renderer.append(issue.issue().getMessage());

    renderer.append(" ");

    renderer.append(SonarLintUtils.age(issue.creationDate()), SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Override public int getIssueCount() {
    return 1;
  }

  @Override public int getFileCount() {
    return 0;
  }

  public IssuePointer issue() {
    return issue;
  }

  private static String issueCoordinates(@Nonnull IssuePointer issue) {
    RangeMarker range = issue.range();
    if (range == null) {
      return "(0, 0) ";
    }

    Document doc = FileDocumentManager.getInstance().getDocument(issue.psiFile().getVirtualFile());
    int line = doc.getLineNumber(range.getStartOffset());
    int offset = range.getStartOffset() - doc.getLineStartOffset(line);
    return String.format("(%d, %d) ", line, offset);
  }
}
