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
package org.sonarlint.intellij.ui.tree;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;

/**
 * Responsible for maintaining the tree model and send change events when needed.
 * Should be optimized to minimize the recreation of portions of the tree.
 *
 * There are 2 implementations within this class
 * - Security Hotspots within a file node (used for the report tab)
 * - Security Hotspots directly child of the summary node (used for the Security Hotspots tab)
 *
 * In the report tab, there is no filtering mechanism, the nodes are simply deleted when needed
 * In the Security Hotspots tab, there is a filtering mechanism that hides or not some nodes
 */
public class SecurityHotspotTreeModelBuilder implements FindingTreeModelBuilder {
  private static final List<VulnerabilityProbability> VULNERABILITY_PROBABILITIES = List.of(VulnerabilityProbability.HIGH,
    VulnerabilityProbability.MEDIUM, VulnerabilityProbability.LOW);
  private static final Comparator<LiveSecurityHotspot> SECURITY_HOTSPOT_COMPARATOR = new SecurityHotspotComparator();
  private static final Comparator<LiveSecurityHotspotNode> SECURITY_HOTSPOT_WITHOUT_FILE_COMPARATOR = new LiveSecurityHotspotNodeComparator();

  protected SecurityHotspotFilters currentFilter = SecurityHotspotFilters.DEFAULT_FILTER;
  private final FindingTreeIndex index;
  private boolean shouldIncludeResolvedHotspots = false;
  private DefaultTreeModel model;
  private SummaryNode summaryNode;
  private TreeSummary treeSummary;
  private List<LiveSecurityHotspotNode> nonFilteredNodes;
  private List<LiveSecurityHotspotNode> filteredNodes;

  public SecurityHotspotTreeModelBuilder() {
    this.index = new FindingTreeIndex();
  }

  /**
   * Creates the model with a basic root
   */
  public DefaultTreeModel createModel(Project project, boolean holdsOldHotspots) {
    treeSummary = new FindingTreeSummary(project, TreeContentKind.SECURITY_HOTSPOTS, holdsOldHotspots);
    summaryNode = new SummaryNode(treeSummary);
    model = new DefaultTreeModel(summaryNode);
    model.setRoot(summaryNode);
    nonFilteredNodes = new ArrayList<>();
    filteredNodes = new ArrayList<>();
    return model;
  }

  public int numberHotspots() {
    return summaryNode.getFindingCount();
  }

  private SummaryNode getFilesParent() {
    return summaryNode;
  }

  public void updateModel(Map<VirtualFile, Collection<LiveSecurityHotspot>> map) {
    var toRemove = index.getAllFiles().stream().filter(f -> !map.containsKey(f)).toList();
    ApplicationManager.getApplication().assertIsDispatchThread();

    nonFilteredNodes.clear();
    toRemove.forEach(this::removeFile);

    var fileWithIssuesCount = 0;
    var issuesCount = 0;
    for (var e : map.entrySet()) {
      var fileIssuesCount = setFileSecurityHotspots(e.getKey(), e.getValue());
      if (fileIssuesCount > 0) {
        issuesCount += fileIssuesCount;
        fileWithIssuesCount++;
      }
    }
    treeSummary.refresh(fileWithIssuesCount, issuesCount);
    model.nodeChanged(summaryNode);
  }

  private int setFileSecurityHotspots(VirtualFile file, Iterable<LiveSecurityHotspot> securityHotspots) {
    if (!accept(file)) {
      removeFile(file);
      return 0;
    }

    var filtered = filter(securityHotspots, false);
    if (filtered.isEmpty()) {
      removeFile(file);
      return 0;
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
      var newIdx = new int[] {idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }
    return filtered.size();
  }

  private void setFileNodeSecurityHotspots(FileNode node, Iterable<LiveSecurityHotspot> securityHotspotsPointer) {
    node.removeAllChildren();

    var securityHotspots = new TreeSet<>(SECURITY_HOTSPOT_COMPARATOR);

    for (var securityHotspot : securityHotspotsPointer) {
      securityHotspots.add(securityHotspot);
    }

    for (var securityHotspot : securityHotspots) {
      var iNode = new LiveSecurityHotspotNode(securityHotspot, false);
      node.add(iNode);

      nonFilteredNodes.add(iNode);
    }
  }

  private void removeFile(VirtualFile file) {
    var node = index.getFileNode(file);

    if (node != null) {
      index.remove(node.file());
      model.removeNodeFromParent(node);
    }
  }

  public LiveSecurityHotspot findFilteredHotspotByKey(String securityHotspotKey) {
    var nodes = summaryNode.children();
    while (nodes.hasMoreElements()) {
      var securityHotspotNode = (LiveSecurityHotspotNode) nodes.nextElement();
      if (securityHotspotKey.equals(securityHotspotNode.getHotspot().getServerKey())) {
        return securityHotspotNode.getHotspot();
      }
    }
    return null;
  }

  public Optional<LiveSecurityHotspot> findHotspotByKey(String securityHotspotKey) {
    return nonFilteredNodes.stream()
      .map(LiveSecurityHotspotNode::getHotspot)
      .filter(hotspot -> hotspot.getServerKey() != null && hotspot.getServerKey().equals(securityHotspotKey))
      .findFirst();
  }

  public int updateModelWithoutFileNode(Map<VirtualFile, Collection<LiveSecurityHotspot>> map) {
    summaryNode.removeAllChildren();

    for (var e : map.entrySet()) {
      setSecurityHotspots(e.getKey(), e.getValue());
    }

    copyToFilteredNodes();
    model.nodeChanged(summaryNode);

    return summaryNode.getFindingCount();
  }

  private void setSecurityHotspots(VirtualFile file, Iterable<LiveSecurityHotspot> securityHotspots) {
    if (!accept(file)) {
      return;
    }

    var filtered = filter(securityHotspots, true);
    if (filtered.isEmpty()) {
      return;
    }

    setRootSecurityHotspots(filtered);
  }

  private void setRootSecurityHotspots(Iterable<LiveSecurityHotspot> securityHotspotsPointer) {
    for (var securityHotspot : securityHotspotsPointer) {
      var iNode = new LiveSecurityHotspotNode(securityHotspot, true);
      var idx = summaryNode.insertLiveSecurityHotspotNode(iNode, SECURITY_HOTSPOT_WITHOUT_FILE_COMPARATOR);
      var newIdx = new int[] {idx};
      model.nodesWereInserted(summaryNode, newIdx);
      model.nodeChanged(summaryNode);
    }
  }

  private void copyToFilteredNodes() {
    nonFilteredNodes.clear();
    Collections.list(summaryNode.children()).forEach(e -> {
      var securityHotspotNode = (LiveSecurityHotspotNode) e;
      nonFilteredNodes.add((LiveSecurityHotspotNode) securityHotspotNode.clone());
    });
  }

  public boolean updateStatusForHotspotWithFileNode(String securityHotspotKey, HotspotStatus status) {
    var optionalNode = nonFilteredNodes
      .stream()
      .filter(node -> securityHotspotKey.equals(node.getHotspot().getServerKey()))
      .findFirst();

    if (optionalNode.isPresent()) {
      var hotspotNode = optionalNode.get();
      var hotspot = hotspotNode.getHotspot();
      hotspot.setStatus(status);
      if (hotspot.isResolved()) {
        var fileNode = (FileNode) hotspotNode.getParent();
        fileNode.remove(hotspotNode);
        if (fileNode.getFindingCount() == 0) {
          index.remove(fileNode.file());
          summaryNode.remove(fileNode);
        }
      }
      model.reload();
      return true;
    }
    return false;
  }

  public Collection<LiveSecurityHotspotNode> getFilteredNodes() {
    return filteredNodes;
  }

  private Collection<VirtualFile> getFilesForNodes() {
    return nonFilteredNodes.stream().map(LiveSecurityHotspotNode::getHotspot).map(LiveSecurityHotspot::file).collect(Collectors.toSet());
  }

  public int updateStatusAndApplyCurrentFiltering(Project project, String securityHotspotKey, HotspotStatus status) {
    for (var securityHotspotNode : nonFilteredNodes) {
      if (securityHotspotKey.equals(securityHotspotNode.getHotspot().getServerKey())) {
        securityHotspotNode.getHotspot().setStatus(status);
        break;
      }
    }
    return applyCurrentFiltering(project);
  }

  public int applyCurrentFiltering(Project project) {
    filteredNodes.clear();
    var fileList = getFilesForNodes();
    Collections.list(summaryNode.children()).forEach(e -> model.removeNodeFromParent((LiveSecurityHotspotNode) e));
    for (var securityHotspotNode : nonFilteredNodes) {
      if (currentFilter.shouldIncludeSecurityHotspot(securityHotspotNode.getHotspot()) && (shouldIncludeResolvedHotspots || !securityHotspotNode.getHotspot().isResolved())) {
        fileList.add(securityHotspotNode.getHotspot().file());
        var idx = summaryNode.insertLiveSecurityHotspotNode(securityHotspotNode, SECURITY_HOTSPOT_WITHOUT_FILE_COMPARATOR);
        var newIdx = new int[] {idx};
        model.nodesWereInserted(summaryNode, newIdx);
        model.nodeChanged(summaryNode);
        filteredNodes.add(securityHotspotNode);
      }
    }
    model.reload();
    treeSummary.refresh(fileList.size(), filteredNodes.size());
    SonarLintUtils.getService(project, CodeAnalyzerRestarter.class).refreshFiles(fileList);
    return filteredNodes.size();
  }

  public int filterSecurityHotspots(Project project, SecurityHotspotFilters filter) {
    currentFilter = filter;
    return applyCurrentFiltering(project);
  }

  public int filterSecurityHotspots(Project project, boolean isResolved) {
    shouldIncludeResolvedHotspots = isResolved;
    return applyCurrentFiltering(project);
  }

  public void clear() {
    treeSummary.reset();
    updateModelWithoutFileNode(Collections.emptyMap());
  }

  private static List<LiveSecurityHotspot> filter(Iterable<LiveSecurityHotspot> securityHotspots, boolean allowResolved) {
    return StreamSupport.stream(securityHotspots.spliterator(), false)
      .filter(hotspot -> accept(hotspot, allowResolved))
      .toList();
  }

  private static boolean accept(LiveSecurityHotspot securityHotspot, boolean allowResolved) {
    if (allowResolved) {
      return securityHotspot.isValid();
    } else {
      return !securityHotspot.isResolved() && securityHotspot.isValid();
    }
  }

  private static boolean accept(VirtualFile file) {
    return file.isValid();
  }

  private static class FileNodeComparator implements Comparator<FileNode> {
    @Override
    public int compare(FileNode o1, FileNode o2) {
      int c = o1.file().getName().compareTo(o2.file().getName());
      if (c != 0) {
        return c;
      }

      return o1.file().getPath().compareTo(o2.file().getPath());
    }
  }

  static class LiveSecurityHotspotNodeComparator implements Comparator<LiveSecurityHotspotNode> {
    @Override
    public int compare(LiveSecurityHotspotNode o1, LiveSecurityHotspotNode o2) {
      int c = o1.getHotspot().getVulnerabilityProbability().compareTo(o2.getHotspot().getVulnerabilityProbability());
      if (c != 0) {
        return c;
      }

      var r1 = o1.getHotspot().getRange();
      var r2 = o2.getHotspot().getRange();

      var rangeStart1 = (r1 == null) ? -1 : r1.getStartOffset();
      var rangeStart2 = (r2 == null) ? -1 : r2.getStartOffset();

      return ComparisonChain.start()
        .compare(o1.getHotspot().file().getPath(), o2.getHotspot().file().getPath())
        .compare(rangeStart1, rangeStart2)
        .compare(o1.getHotspot().getRuleKey(), o2.getHotspot().getRuleKey())
        .compare(o1.getHotspot().uid(), o2.getHotspot().uid())
        .result();
    }
  }

  static class SecurityHotspotComparator implements Comparator<LiveSecurityHotspot> {
    @Override
    public int compare(@Nonnull LiveSecurityHotspot o1, @Nonnull LiveSecurityHotspot o2) {
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

    if (next instanceof LiveSecurityHotspotNode hotspotNode) {
      return hotspotNode;
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

    if (next instanceof LiveSecurityHotspotNode hotspotNode) {
      return hotspotNode;
    }

    return lastHotspotDown(next);
  }

  /**
   * Finds the first Security Hotspot node which is child of a given node.
   */
  @CheckForNull
  private static LiveSecurityHotspotNode firstHotspotDown(AbstractNode node) {
    if (node instanceof LiveSecurityHotspotNode hotspotNode) {
      return hotspotNode;
    }

    if (node.getChildCount() > 0) {
      var firstChild = node.getFirstChild();
      return firstHotspotDown((AbstractNode) firstChild);
    }

    return null;
  }

  /**
   * Finds the first Security Hotspot node which is child of a given node.
   */
  @CheckForNull
  private static LiveSecurityHotspotNode lastHotspotDown(AbstractNode node) {
    if (node instanceof LiveSecurityHotspotNode hotspotNode) {
      return hotspotNode;
    }

    var lastChild = node.getLastChild();

    if (lastChild == null) {
      return null;
    }

    return lastHotspotDown((AbstractNode) lastChild);
  }
}
