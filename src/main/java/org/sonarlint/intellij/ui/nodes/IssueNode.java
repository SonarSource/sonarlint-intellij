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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.SonarLintIcons;
import javax.annotation.Nonnull;
import javax.swing.Icon;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

import java.util.Locale;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;

public class IssueNode extends AbstractNode {
  // not available in IJ15
  private static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER, UIUtil.getInactiveTextColor());

  private final LiveIssue issue;

  public IssueNode(LiveIssue issue) {
    this.issue = issue;
  }

  @Override public void render(TreeCellRenderer renderer) {
    String severity = StringUtil.capitalize(issue.getSeverity().toLowerCase(Locale.ENGLISH));
    String type = issue.getType();

    if (type != null) {
      String typeStr = type.replace('_', ' ').toLowerCase(Locale.ENGLISH);
      renderer.setIconToolTip(severity + " " + typeStr);
      int gap = JBUI.isHiDPI() ? 8 : 4;
      setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, SonarLintIcons.type12(type), SonarLintIcons.severity12(severity)));
    } else {
      renderer.setIconToolTip(severity);
      setIcon(renderer, SonarLintIcons.severity12(severity));
    }

    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);

    if (issue.isValid()) {
      renderer.setToolTipText("Double click to open location");
      renderer.append(issue.getMessage());
    } else {
      renderer.setToolTipText("Issue is no longer valid");
      renderer.append(issue.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    if (!issue.flows().isEmpty()) {
      int numLocations = issue.flows().stream().mapToInt(f -> f.locations().size()).sum();
      String flows = String.format(" [+%d %s]", numLocations, SonarLintUtils.pluralize("location", numLocations));
      renderer.append(flows, GRAYED_SMALL_ATTRIBUTES);
    }

    if (issue.getCreationDate() != null) {
      renderer.append(" ");
      String creationDate = DateUtils.toAge(issue.getCreationDate());
      renderer.append(creationDate, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private void setIcon(TreeCellRenderer renderer, Icon icon) {
    if (issue.isValid()) {
      renderer.setIcon(icon);
    } else {
      renderer.setIcon(SonarLintIcons.toDisabled(icon));
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
