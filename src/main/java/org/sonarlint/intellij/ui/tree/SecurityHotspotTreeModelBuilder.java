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
package org.sonarlint.intellij.ui.tree;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

/**
 * Responsible for maintaining the tree model and send change events when needed.
 * Should be optimized to minimize the recreation of portions of the tree.
 */
public class SecurityHotspotTreeModelBuilder implements FindingTreeModelBuilder {
  private static final List<VulnerabilityProbability> VULNERABILITY_PROBABILITIES = List.of(VulnerabilityProbability.HIGH,
    VulnerabilityProbability.MEDIUM, VulnerabilityProbability.LOW);
  private static final Comparator<LiveSecurityHotspot> SECURITY_HOTSPOT_COMPARATOR = new SecurityHotspotComparator();

  private final FindingTreeIndex index;
  private DefaultTreeModel model;
  private SummaryNode summary;

  public SecurityHotspotTreeModelBuilder() {
    this.index = new FindingTreeIndex();
  }

  /**
   * Creates the model with a basic root
   */
  public DefaultTreeModel createModel() {
    summary = new SummaryNode(true);
    model = new DefaultTreeModel(summary);
    model.setRoot(summary);
    return model;
  }

  public int numberHotspots() {
    return summary.getFindingCount();
  }

  private SummaryNode getFilesParent() {
    return summary;
  }

  public void updateModel(Map<VirtualFile, Collection<LiveSecurityHotspot>> map, String emptyText) {
    summary.setEmptyText(emptyText);

    var toRemove = index.getAllFiles().stream().filter(f -> !map.containsKey(f)).collect(Collectors.toList());

    toRemove.forEach(this::removeFile);

    for (var e : map.entrySet()) {
      setFileSecurityHotspots(e.getKey(), e.getValue());
    }

    model.nodeChanged(summary);
  }

  private void setFileSecurityHotspots(VirtualFile file, Iterable<LiveSecurityHotspot> securityHotspots) {
    if (!accept(file)) {
      removeFile(file);
      return;
    }

    var filtered = filter(securityHotspots);
    if (filtered.isEmpty()) {
      removeFile(file);
      return;
    }

    var newFile = false;
    var fNode = index.getFileNode(file);
    if (fNode == null) {
      newFile = true;
      fNode = new FileNode(file, true);
      index.setFileNode(fNode);
    }

    setFileNodeSecurityHotspots(fNode, filtered);

    if (newFile) {
      var parent = getFilesParent();
      var idx = parent.insertFileNode(fNode, new FileNodeComparator());
      var newIdx = new int[]{idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }
  }

  private static void setFileNodeSecurityHotspots(FileNode node, Iterable<LiveSecurityHotspot> securityHotspotsPointer) {
    node.removeAllChildren();

    var securityHotspots = new TreeSet<>(SECURITY_HOTSPOT_COMPARATOR);

    for (var securityHotspot : securityHotspotsPointer) {
      securityHotspots.add(securityHotspot);
    }

    for (var securityHotspot : securityHotspots) {
      var iNode = new LiveSecurityHotspotNode(securityHotspot, false);
      node.add(iNode);
    }
  }

  private void removeFile(VirtualFile file) {
    var node = index.getFileNode(file);

    if (node != null) {
      index.remove(node.file());
      model.removeNodeFromParent(node);
    }
  }

  public LiveSecurityHotspot findHotspot(String securityHotspotKey) {
    var nodes = summary.children();
    while (nodes.hasMoreElements()) {
      var securityHotspotNode = (LiveSecurityHotspotNode) nodes.nextElement();
      if (securityHotspotKey.equals(securityHotspotNode.getHotspot().getServerFindingKey())) {
        return securityHotspotNode.getHotspot();
      }
    }
    return null;
  }

  public int updateModelWithoutFileNode(Map<VirtualFile, Collection<LiveSecurityHotspot>> map, String emptyText) {
    summary.setEmptyText(emptyText);

    var nodes = summary.children();
    while (nodes.hasMoreElements()) {
      var securityHotspotNode = (LiveSecurityHotspotNode) nodes.nextElement();
      if (!map.containsKey(securityHotspotNode.getHotspot().getFile())) {
        model.removeNodeFromParent(securityHotspotNode);
      }
    }

    for (var e : map.entrySet()) {
      setSecurityHotspots(e.getKey(), e.getValue());
    }

    model.nodeChanged(summary);

    return summary.getFindingCount();
  }

  private void setSecurityHotspots(VirtualFile file, Iterable<LiveSecurityHotspot> securityHotspots) {
    if (!accept(file)) {
      removeHotspotsByFile(file);
      return;
    }

    var filtered = filter(securityHotspots);
    if (filtered.isEmpty()) {
      removeHotspotsByFile(file);
      return;
    }

    setRootSecurityHotspots(file, filtered);
  }

  private void removeHotspotsByFile(VirtualFile file) {
    Collections.list(summary.children()).forEach(e -> {
      var securityHotspotNode = (LiveSecurityHotspotNode) e;
      if (securityHotspotNode.getHotspot().getFile().equals(file)) {
        model.removeNodeFromParent(securityHotspotNode);
      }
    });
  }

  private void setRootSecurityHotspots(VirtualFile file, Iterable<LiveSecurityHotspot> securityHotspotsPointer) {
    removeHotspotsByFile(file);

    for (var securityHotspot : securityHotspotsPointer) {
      var iNode = new LiveSecurityHotspotNode(securityHotspot, true);
      var idx = summary.insertLiveSecurityHotspotNode(iNode, new LiveSecurityHotspotNodeComparator());
      var newIdx = new int[]{idx};
      model.nodesWereInserted(summary, newIdx);
      model.nodeChanged(summary);
    }
  }

  public void clear() {
    updateModel(Collections.emptyMap(), "No analysis done");
  }

  private static List<LiveSecurityHotspot> filter(Iterable<LiveSecurityHotspot> securityHotspots) {
    return StreamSupport.stream(securityHotspots.spliterator(), false)
      .filter(SecurityHotspotTreeModelBuilder::accept)
      .collect(Collectors.toList());
  }

  private static boolean accept(LiveSecurityHotspot securityHotspot) {
    return !securityHotspot.isResolved() && securityHotspot.isValid();
  }

  private static boolean accept(VirtualFile file) {
    return file.isValid();
  }

  private static class FileNodeComparator implements Comparator<FileNode> {
    @Override public int compare(FileNode o1, FileNode o2) {
      int c = o1.file().getName().compareTo(o2.file().getName());
      if (c != 0) {
        return c;
      }

      return o1.file().getPath().compareTo(o2.file().getPath());
    }
  }

  private static class LiveSecurityHotspotNodeComparator implements Comparator<LiveSecurityHotspotNode> {
    @Override public int compare(LiveSecurityHotspotNode o1, LiveSecurityHotspotNode o2) {
      int c = o1.getHotspot().getVulnerabilityProbability().compareTo(o2.getHotspot().getVulnerabilityProbability());
      if (c != 0) {
        return c;
      }

      var r1 = o1.getHotspot().getRange();
      var r2 = o2.getHotspot().getRange();

      var rangeStart1 = (r1 == null) ? -1 : r1.getStartOffset();
      var rangeStart2 = (r2 == null) ? -1 : r2.getStartOffset();

      return ComparisonChain.start()
        .compare(rangeStart1, rangeStart2)
        .compare(o1.getHotspot().getRuleKey(), o2.getHotspot().getRuleKey())
        .compare(o1.getHotspot().uid(), o2.getHotspot().uid())
        .result();
    }
  }

  static class SecurityHotspotComparator implements Comparator<LiveSecurityHotspot> {
    @Override public int compare(@Nonnull LiveSecurityHotspot o1, @Nonnull LiveSecurityHotspot o2) {
      var introductionDateOrdering = Ordering.natural().reverse().nullsLast();
      var dateCompare = introductionDateOrdering.compare(o1.getIntroductionDate(), o2.getIntroductionDate());

      if (dateCompare != 0) {
        return dateCompare;
      }

      var vulnerabilityCompare = Ordering.explicit(VULNERABILITY_PROBABILITIES)
        .compare(o1.getVulnerabilityProbability(), o2.getVulnerabilityProbability());

      if (vulnerabilityCompare != 0) {
        return vulnerabilityCompare;
      }

      var r1 = o1.getRange();
      var r2 = o2.getRange();

      var rangeStart1 = (r1 == null) ? -1 : r1.getStartOffset();
      var rangeStart2 = (r2 == null) ? -1 : r2.getStartOffset();

      return ComparisonChain.start()
        .compare(rangeStart1, rangeStart2)
        .compare(o1.getRuleKey(), o2.getRuleKey())
        .compare(o1.uid(), o2.uid())
        .result();
    }
  }

  @CheckForNull
  public LiveSecurityHotspotNode getNextHotspot(AbstractNode startNode) {
    if (!(startNode instanceof LiveSecurityHotspotNode)) {
      return firstHotspotDown(startNode);
    }

    var next = getNextNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof LiveSecurityHotspotNode) {
      return (LiveSecurityHotspotNode) next;
    }

    return firstHotspotDown(next);
  }

  @CheckForNull
  public LiveSecurityHotspotNode getPreviousHotspot(AbstractNode startNode) {
    var next = getPreviousNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof LiveSecurityHotspotNode) {
      return (LiveSecurityHotspotNode) next;
    }

    return lastHotspotDown(next);
  }

  /**
   * Finds the first security hotspot node which is child of a given node.
   */
  @CheckForNull
  private static LiveSecurityHotspotNode firstHotspotDown(AbstractNode node) {
    if (node instanceof LiveSecurityHotspotNode) {
      return (LiveSecurityHotspotNode) node;
    }

    if (node.getChildCount() > 0) {
      var firstChild = node.getFirstChild();
      return firstHotspotDown((AbstractNode) firstChild);
    }

    return null;
  }

  /**
   * Finds the first security hotspot node which is child of a given node.
   */
  @CheckForNull
  private static LiveSecurityHotspotNode lastHotspotDown(AbstractNode node) {
    if (node instanceof LiveSecurityHotspotNode) {
      return (LiveSecurityHotspotNode) node;
    }

    var lastChild = node.getLastChild();

    if (lastChild == null) {
      return null;
    }

    return lastHotspotDown((AbstractNode) lastChild);
  }
}
