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
package org.sonarlint.intellij.ui.tree;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;

/**
 * Responsible for maintaining the tree model and send change events when needed.
 * Should be optimize to minimize the recreation of portions of the tree.
 */
public class IssueTreeModelBuilder {
  private static final List<String> SEVERITY_ORDER = ImmutableList.of("BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO");
  private static final Comparator<LiveIssue> ISSUE_COMPARATOR = new IssueComparator();

  private final IssueTreeIndex index;
  private DefaultTreeModel model;
  private SummaryNode summary;

  public IssueTreeModelBuilder() {
    this.index = new IssueTreeIndex();
  }

  /**
   * Creates the model with a basic root
   */
  public DefaultTreeModel createModel() {
    summary = new SummaryNode();
    model = new DefaultTreeModel(summary);
    model.setRoot(summary);
    return model;
  }

  public int numberIssues() {
    return summary.getIssueCount();
  }

  private AbstractNode getFilesParent() {
    return summary;
  }

  public void updateModel(Map<VirtualFile, Collection<LiveIssue>> map, String emptyText) {
    summary.setEmptyText(emptyText);

    List<VirtualFile> toRemove = index.getAllFiles().stream().filter(f -> !map.containsKey(f)).collect(Collectors.toList());

    toRemove.forEach(this::removeFile);

    for (Map.Entry<VirtualFile, Collection<LiveIssue>> e : map.entrySet()) {
      setFileIssues(e.getKey(), e.getValue());
    }

    model.nodeChanged(summary);
  }

  public void updateEmptyText(String emptyText) {
    summary.setEmptyText(emptyText);
    if (summary.isLeaf()) {
      model.nodeChanged(summary);
    }
  }

  private void setFileIssues(VirtualFile file, Iterable<LiveIssue> issues) {
    if (!accept(file)) {
      removeFile(file);
      return;
    }

    List<LiveIssue> filtered = filter(issues);
    if (filtered.isEmpty()) {
      removeFile(file);
      return;
    }

    boolean newFile = false;
    FileNode fNode = index.getFileNode(file);
    if (fNode == null) {
      newFile = true;
      fNode = new FileNode(file);
      index.setFileNode(fNode);
    }

    setIssues(fNode, filtered);

    if (newFile) {
      AbstractNode parent = getFilesParent();
      int idx = parent.getInsertIdx(fNode, new FileNodeComparator());
      int[] newIdx = {idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }
  }

  private void removeFile(VirtualFile file) {
    FileNode node = index.getFileNode(file);

    if (node != null) {
      index.remove(node.file());
      model.removeNodeFromParent(node);
    }
  }

  private static void setIssues(FileNode node, Iterable<LiveIssue> issuePointers) {
    node.removeAllChildren();

    // 15ms for 500 issues -> to improve?
    TreeSet<LiveIssue> set = new TreeSet<>(ISSUE_COMPARATOR);

    for (LiveIssue issue : issuePointers) {
      set.add(issue);
    }

    for (LiveIssue issue : set) {
      IssueNode iNode = new IssueNode(issue);
      node.add(iNode);
    }
  }

  private static List<LiveIssue> filter(Iterable<LiveIssue> issues) {
    return StreamSupport.stream(issues.spliterator(), false)
      .filter(IssueTreeModelBuilder::accept)
      .collect(Collectors.toList());
  }

  private static boolean accept(LiveIssue issue) {
    return !issue.isResolved() && issue.isValid();
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

  static class IssueComparator implements Comparator<LiveIssue> {
    @Override public int compare(@Nonnull LiveIssue o1, @Nonnull LiveIssue o2) {
      Ordering<Long> creationDateOrdering = Ordering.natural().reverse().nullsLast();
      int dateCompare = creationDateOrdering.compare(o1.getCreationDate(), o2.getCreationDate());

      if (dateCompare != 0) {
        return dateCompare;
      }

      int severityCompare = Ordering.explicit(SEVERITY_ORDER).compare(o1.getSeverity(), o2.getSeverity());

      if (severityCompare != 0) {
        return severityCompare;
      }

      RangeMarker r1 = o1.getRange();
      RangeMarker r2 = o2.getRange();

      int rangeStart1 = (r1 == null) ? -1 : r1.getStartOffset();
      int rangeStart2 = (r2 == null) ? -1 : r2.getStartOffset();

      return ComparisonChain.start()
        .compare(rangeStart1, rangeStart2)
        .compare(o1.getRuleName(), o2.getRuleName())
        .compare(o1.uid(), o2.uid())
        .result();
    }
  }

  @CheckForNull
  public IssueNode getNextIssue(AbstractNode<?> startNode) {
    if (!(startNode instanceof IssueNode)) {
      return firstIssueDown(startNode);
    }

    Object next = getNextNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof IssueNode) {
      return (IssueNode) next;
    }

    return firstIssueDown((AbstractNode) next);
  }

  @CheckForNull
  public IssueNode getPreviousIssue(AbstractNode<?> startNode) {
    Object next = getPreviousNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof IssueNode) {
      return (IssueNode) next;
    }

    return lastIssueDown((AbstractNode) next);
  }

  /**
   * Finds the first issue node which is child of a given node.
   */
  @CheckForNull
  private static IssueNode firstIssueDown(AbstractNode node) {
    if (node instanceof IssueNode) {
      return (IssueNode) node;
    }

    if (node.getChildCount() > 0) {
      TreeNode firstChild = node.getFirstChild();
      return firstIssueDown((AbstractNode) firstChild);
    }

    return null;
  }

  /**
   * Finds the first issue node which is child of a given node.
   */
  @CheckForNull
  private static IssueNode lastIssueDown(AbstractNode node) {
    if (node instanceof IssueNode) {
      return (IssueNode) node;
    }

    TreeNode lastChild = node.getLastChild();

    if (lastChild == null) {
      return null;
    }

    return lastIssueDown((AbstractNode) lastChild);
  }

  @CheckForNull
  private static AbstractNode getPreviousNode(AbstractNode startNode) {
    AbstractNode parent = (AbstractNode) startNode.getParent();

    if (parent == null) {
      return null;
    }
    TreeNode previous = parent.getChildBefore(startNode);
    if (previous == null) {
      return getPreviousNode(parent);
    }

    return (AbstractNode) previous;
  }

  /**
   * Next node, either the sibling if it exists, or the sibling of the parent
   */
  @CheckForNull
  private static AbstractNode getNextNode(AbstractNode startNode) {
    AbstractNode parent = (AbstractNode) startNode.getParent();

    if (parent == null) {
      return null;
    }
    TreeNode after = parent.getChildAfter(startNode);
    if (after == null) {
      return getNextNode(parent);
    }

    return (AbstractNode) after;
  }
}
