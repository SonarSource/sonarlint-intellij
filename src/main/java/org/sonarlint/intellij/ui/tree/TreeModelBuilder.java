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
package org.sonarlint.intellij.ui.tree;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;

/**
 * Responsible for maintaining the tree model and send change events when needed.
 * Should be optimize to minimize the recreation of portions of the tree.
 */
public class TreeModelBuilder {
  private static final List<String> SEVERITY_ORDER = ImmutableList.of("BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO");
  private static final Comparator<IssuePointer> ISSUE_COMPARATOR = new IssueComparator();

  private DefaultTreeModel model;
  private SummaryNode summary;
  private IssueTreeIndex index;
  private Condition<VirtualFile> condition;

  public TreeModelBuilder() {
    this.index = new IssueTreeIndex();
  }

  public void updateFiles(Map<VirtualFile, Collection<IssuePointer>> issuesPerFile) {
    for (Map.Entry<VirtualFile, Collection<IssuePointer>> e : issuesPerFile.entrySet()) {
      setFileIssues(e.getKey(), e.getValue(), condition);
    }

    model.nodeChanged(summary);
  }

  @CheckForNull
  public IssueNode getNextIssue(AbstractNode<?> startNode) {
    if (!(startNode instanceof IssueNode)) {
      return firstIssueDown((AbstractNode) startNode);
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

  /**
   * Creates the model with a basic root
   */
  public DefaultTreeModel createModel() {
    summary = new SummaryNode();

    model = new DefaultTreeModel(summary);
    model.setRoot(summary);
    return model;
  }

  private AbstractNode getParent() {
    return summary;
  }

  public DefaultTreeModel updateModel(Map<VirtualFile, Collection<IssuePointer>> map, @Nullable Condition<VirtualFile> condition) {
    this.condition = condition;

    List<VirtualFile> toRemove = new LinkedList<>();
    for (VirtualFile f : index.getAllFiles()) {
      if (!map.containsKey(f)) {
        toRemove.add(f);
      }
    }


    toRemove.forEach(this::removeFile);

    for (Map.Entry<VirtualFile, Collection<IssuePointer>> e : map.entrySet()) {
      setFileIssues(e.getKey(), e.getValue(), condition);
    }

    return model;
  }

  private FileNode setFileIssues(VirtualFile file, Iterable<IssuePointer> issues, @Nullable Condition<VirtualFile> condition) {
    if (!accept(file, condition)) {
      removeFile(file);
      return null;
    }

    List<IssuePointer> filtered = filter(issues);
    if (filtered.isEmpty()) {
      removeFile(file);
      return null;
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
      AbstractNode parent = getParent();
      int idx = parent.getInsertIdx(fNode, new FileNodeComparator());
      int[] newIdx = {idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }

    return fNode;
  }

  private void removeFile(VirtualFile file) {
    FileNode node = index.getFileNode(file);

    if (node != null) {
      index.remove(node.file());
      model.removeNodeFromParent(node);
    }
  }

  private static void setIssues(FileNode node, Iterable<IssuePointer> issuePointers) {
    node.removeAllChildren();

    // 15ms for 500 issues -> to improve?
    TreeSet<IssuePointer> set = new TreeSet<>(ISSUE_COMPARATOR);

    for (IssuePointer issue : issuePointers) {
      set.add(issue);
    }

    for (IssuePointer issue : set) {
      IssueNode iNode = new IssueNode(issue);
      node.add(iNode);
    }
  }

  private static List<IssuePointer> filter(Iterable<IssuePointer> issues) {
    List<IssuePointer> filtered = new ArrayList<>();
    for (IssuePointer ip : issues) {
      if (!accept(ip)) {
        continue;
      }

      filtered.add(ip);
    }

    return filtered;
  }

  private static boolean accept(IssuePointer issue) {
    return issue.isValid();
  }

  private static boolean accept(VirtualFile file, @Nullable Condition<VirtualFile> condition) {
    if (!file.isValid()) {
      return false;
    }

    if (condition == null) {
      return true;
    }

    return condition.value(file);
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

  static class IssueComparator implements Comparator<IssuePointer> {
    @Override public int compare(@Nonnull IssuePointer o1, @Nonnull IssuePointer o2) {
      Ordering<Long> creationDateOrdering = Ordering.natural().reverse().nullsLast();
      int dateCompare = creationDateOrdering.compare(o1.creationDate(), o2.creationDate());

      if (dateCompare != 0) {
        return dateCompare;
      }

      int severityCompare = Ordering.explicit(SEVERITY_ORDER).compare(o1.issue().getSeverity(), o2.issue().getSeverity());

      if (severityCompare != 0) {
        return severityCompare;
      }

      RangeMarker r1 = o1.range();
      RangeMarker r2 = o2.range();

      int rangeStart1 = (r1 == null) ? -1 : r1.getStartOffset();
      int rangeStart2 = (r2 == null) ? -1 : r2.getStartOffset();

      return ComparisonChain.start()
        .compare(o1.issue().getRuleName(), o2.issue().getRuleName())
        .compare(rangeStart1, rangeStart2)
        .compare(o1.uid(), o2.uid())
        .result();
    }
  }
}
