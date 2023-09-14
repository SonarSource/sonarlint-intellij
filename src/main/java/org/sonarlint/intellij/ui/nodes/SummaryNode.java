/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.SummaryNodeType;

public class SummaryNode extends AbstractNode {
  private static final String FORMAT = "Found %d %s in %d %s";
  private String emptyText;
  private final SummaryNodeType type;

  public SummaryNode() {
    super();
    this.emptyText = "No issues to display";
    this.type = SummaryNodeType.NEW_ISSUE;
  }

  public SummaryNode(SummaryNodeType type) {
    super();
    if (type.equals(SummaryNodeType.NEW_SECURITY_HOTSPOT) || type.equals(SummaryNodeType.OLD_SECURITY_HOTSPOT)) {
      this.emptyText = "No Security Hotspots to display";
    } else {
      this.emptyText = "No issues to display";
    }

    this.type = type;
  }

  public void setEmptyText(String emptyText) {
    this.emptyText = emptyText;
  }

  public String getEmptyText() {
    return emptyText;
  }

  public String getText() {
    var findings = getFindingCount();
    var files = getChildCount();

    if (findings == 0) {
      return emptyText;
    }

    return getTextString(findings, files, type);
  }

  private String getTextString(int findings, int files, SummaryNodeType type) {
    if (type.equals(SummaryNodeType.NEW_SECURITY_HOTSPOT) || type.equals(SummaryNodeType.OLD_SECURITY_HOTSPOT)) {
      var sinceTextHotspots = type.equals(SummaryNodeType.OLD_SECURITY_HOTSPOT) ? "since old analysis" : "since new analysis";
      return String.format(FORMAT, findings, findings == 1 ? "Security Hotspot" : "Security Hotspots", files,
        files == 1 ? ("file " + sinceTextHotspots) : ("files " + sinceTextHotspots));

    }

    var sinceTextIssues = type.equals(SummaryNodeType.OLD_ISSUE) ? "since old analysis" : "since new analysis";
    return String.format(FORMAT, findings, findings == 1 ? "issue" : "issues", files, files == 1 ? ("file " + sinceTextIssues) : ("files " + sinceTextIssues));
  }

  public int insertFileNode(FileNode newChild, Comparator<FileNode> comparator) {
    if (children == null) {
      insert(newChild, 0);
      return 0;
    }

    var nodes = children.stream().map(n -> (FileNode) n).collect(Collectors.<FileNode>toList());
    var foundIndex = Collections.binarySearch(nodes, newChild, comparator);
    if (foundIndex >= 0) {
      throw new IllegalArgumentException("Child already exists");
    }

    int insertIdx = -foundIndex - 1;
    insert(newChild, insertIdx);
    return insertIdx;
  }

  public int insertLiveSecurityHotspotNode(LiveSecurityHotspotNode newChild, Comparator<LiveSecurityHotspotNode> comparator) {
    if (children == null) {
      insert(newChild, 0);
      return 0;
    }

    var nodes = children.stream().map(n -> (LiveSecurityHotspotNode) n).collect(Collectors.<LiveSecurityHotspotNode>toList());
    var foundIndex = Collections.binarySearch(nodes, newChild, comparator);
    if (foundIndex >= 0) {
      throw new IllegalArgumentException("Child already exists");
    }

    int insertIdx = -foundIndex - 1;
    insert(newChild, insertIdx);
    return insertIdx;
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    renderer.append(getText());
    renderer.setToolTipText(null);
  }

  @Override
  public String toString() {
    return getText();
  }
}
