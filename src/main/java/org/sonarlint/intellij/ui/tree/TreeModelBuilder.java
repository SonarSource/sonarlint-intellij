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

import com.google.common.base.Function;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;

import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class TreeModelBuilder {
  private final Project project;
  private DefaultTreeModel model;
  private SummaryNode summary;
  private IssueTreeIndex index;
  private Condition<VirtualFile> condition;

  private Comparator<IssuePointer> issuePointerComparator = new Comparator<IssuePointer>() {
    private final List<String> severityOrder = ImmutableList.of("BLOCKER", "CRITICAL", "MAJOR","MINOR","INFO");
    private final Ordering<IssuePointer> ordering = Ordering.explicit(severityOrder).onResultOf(new IssueSeverityExtractor())
      .compound(new IssueComparator());

    @Override public int compare(IssuePointer o1, IssuePointer o2) {
      return ordering.compare(o1, o2);
    }
  };

  public TreeModelBuilder(Project project) {
    this.project = project;
    this.index = new IssueTreeIndex();
  }

  public void updateFiles(Map<VirtualFile, Collection<IssuePointer>> issuesPerFile) {
    for(Map.Entry<VirtualFile, Collection<IssuePointer>> e : issuesPerFile.entrySet() ) {
      setFileIssues(e.getKey(), e.getValue(), condition);
    }

    model.nodeChanged(summary);
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

  public DefaultTreeModel updateModel(Map<VirtualFile, Collection<IssuePointer>> map, Condition<VirtualFile> condition) {
    this.condition = condition;

    for(VirtualFile f : index.getAllFiles()) {
      if(!map.containsKey(f)) {
        removeFile(f);
      }
    }

    for(Map.Entry<VirtualFile, Collection<IssuePointer>> e : map.entrySet()) {
      setFileIssues(e.getKey(), e.getValue(), condition);
    }

    return model;
  }

  private FileNode setFileIssues(VirtualFile file, Iterable<IssuePointer> issues, Condition<VirtualFile> condition) {
    if(!accept(file, condition)) {
      removeFile(file);
      return null;
    }

    List<IssuePointer> filtered = filter(issues);
    if(filtered.isEmpty()) {
      removeFile(file);
      return null;
    }

    boolean newFile = false;
    FileNode fNode = index.getFileNode(file);
    if(fNode == null) {
      newFile = true;
      fNode = new FileNode(file);
      index.setFileNode(fNode);
    }

    setIssues(fNode, filtered);

    if(newFile) {
      AbstractNode parent = getParent();
      int idx = parent.getInsertIdx(fNode, new FileNodeComparator());
      int[] newIdx = { idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }

    return fNode;
  }

  private void removeFile(VirtualFile file) {
    FileNode node = index.getFileNode(file);

    if(node != null) {
      index.remove(node.file());
      model.removeNodeFromParent(node);
    }
  }

  private void setIssues(FileNode node, Iterable<IssuePointer> issuePointers) {
    node.removeAllChildren();

    // 15ms for 500 issues -> to improve?
    TreeSet<IssuePointer> set = new TreeSet<>(issuePointerComparator);

    for(IssuePointer issue :issuePointers) {
      set.add(issue);
    }

    for(IssuePointer issue : set) {
      IssueNode iNode = new IssueNode(issue);
      node.add(iNode);
    }
  }

  private static List<IssuePointer> filter(Iterable<IssuePointer> issues) {
    List<IssuePointer> filtered = new ArrayList<>();
    for(IssuePointer ip : issues) {
      if(!accept(ip)) {
        continue;
      }

      filtered.add(ip);
    }

    return filtered;
  }

  private static boolean accept(IssuePointer issue) {
    return issue.isValid();
  }

  private static boolean accept(VirtualFile file, Condition<VirtualFile> condition) {
    if(!file.isValid()) {
      return false;
    }

    return condition.value(file);
  }

  public void updateFile(VirtualFile file) {
    FileNode node = index.getFileNode(file);
    if(node != null) {
      model.nodeStructureChanged(node);
    }
  }

  private class FileNodeComparator implements Comparator<FileNode> {
    @Override public int compare(FileNode o1, FileNode o2) {
      int c = o1.file().getName().compareTo(o2.file().getName());
      if(c != 0) {
        return c;
      }

      return o1.file().getCanonicalPath().compareTo(o2.file().getCanonicalPath());
    }
  }

  private static class IssueSeverityExtractor implements Function<IssuePointer, String> {
    @Nullable @Override public String apply(IssuePointer o) {
      return o.issue().getSeverity();
    }
  }

  private class IssueComparator implements Comparator<IssuePointer> {
    @Override public int compare(IssuePointer o1, IssuePointer o2) {
      int rangeStart1 = (o1.range() == null) ? -1 : o1.range().getStartOffset();
      int rangeStart2 = (o2.range() == null) ? -1 : o2.range().getStartOffset();

      return ComparisonChain.start().compare(o1.issue().getRuleName(), o2.issue().getRuleName())
        .compare(rangeStart1, rangeStart2)
        .compare(o1.uid(), o2.uid())
        .result();
    }
  }
}
