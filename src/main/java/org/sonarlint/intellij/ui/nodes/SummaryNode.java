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

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SummaryNode extends AbstractNode {
  private static final String FORMAT = "Found %d %s%s in %d %s%s";
  private String emptyText;
  private final boolean forSecurityHotspot;
  private final boolean containsOldFindings;

  public SummaryNode() {
    super();
    containsOldFindings = false;
    this.emptyText = "No issues to display";
    this.forSecurityHotspot = false;
  }

  public SummaryNode(boolean forSecurityHotspot, boolean containsOldFindings) {
    super();
    this.containsOldFindings = containsOldFindings;
    if (forSecurityHotspot) {
      this.emptyText = "No Security Hotspots to display";
    } else {
      this.emptyText = "No issues to display";
    }
    this.forSecurityHotspot = forSecurityHotspot;
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

    String sinceText = "";
    String newOrOldOrNothing = "";
    if (getGlobalSettings().isFocusOnNewCode()) {
      sinceText = containsOldFindings ? "" : " since [XXX use NCD from backend]";
      newOrOldOrNothing = containsOldFindings ? "older " : "new ";
    }

    return String.format(FORMAT, findings, newOrOldOrNothing, pluralize(forSecurityHotspot ? "Security Hotspot" : "issue", findings), files, pluralize("file", files), sinceText);
  }

  private static String pluralize(String word, int count) {
    return count == 1 ? word : word + "s";
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
