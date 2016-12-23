/*
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import icons.SonarLintIcons;
import javax.annotation.Nonnull;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class IssueNode extends AbstractNode {
  private final LiveIssue issue;

  public IssueNode(LiveIssue issue) {
    this.issue = issue;
  }

  @Override public void render(ColoredTreeCellRenderer renderer) {
    String severity = issue.getSeverity();

    if (severity != null) {
      renderer.setIcon(SonarLintIcons.severity(severity));
    }
    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);

    if (issue.isValid()) {
      renderer.append(issue.getMessage());
    } else {
      renderer.append(issue.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    renderer.append(" ");

    if (issue.getCreationDate() != null) {
      String creationDate = SonarLintUtils.age(issue.getCreationDate());
      renderer.append(creationDate, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override public int getIssueCount() {
    return 1;
  }

  @Override public int getFileCount() {
    return 0;
  }

  public LiveIssue issue() {
    return issue;
  }

  private static String issueCoordinates(@Nonnull LiveIssue issue) {
    RangeMarker range = issue.getRange();
    if (range == null) {
      return "(0, 0) ";
    }

    if (!issue.isValid()) {
      return "(-, -) ";
    }

    Document doc = range.getDocument();
    int line = doc.getLineNumber(range.getStartOffset());
    int offset = range.getStartOffset() - doc.getLineStartOffset(line);
    return String.format("(%d, %d) ", line + 1, offset);
  }
}
