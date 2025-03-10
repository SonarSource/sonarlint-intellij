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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.HIGH;
import static org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.LOW;
import static org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.MEDIUM;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.BLOCKER;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.CRITICAL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.INFO;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MINOR;

/**
 * Responsible for maintaining the tree model and send change events when needed.
 * Should be optimize to minimize the recreation of portions of the tree.
 */
public class IssueTreeModelBuilder implements FindingTreeModelBuilder {
  private static final List<IssueSeverity> SEVERITY_ORDER = List.of(BLOCKER, CRITICAL, MAJOR, MINOR, INFO);
  private static final List<ImpactSeverity> IMPACT_ORDER = List.of(ImpactSeverity.BLOCKER, HIGH, MEDIUM, LOW, ImpactSeverity.INFO);
  private static final Comparator<LiveIssue> ISSUE_COMPARATOR = new IssueComparator();

  private final FindingTreeIndex index;
  private final Project project;
  private DefaultTreeModel model;
  private SummaryNode summaryNode;
  private boolean includeLocallyResolvedIssues = false;
  private Map<VirtualFile, Collection<LiveIssue>> latestIssues;
  private TreeSummary treeSummary;
  private int issueCount;

  public IssueTreeModelBuilder(Project project) {
    this.project = project;
    this.index = new FindingTreeIndex();
    this.issueCount = 0;
  }

  /**
   * Creates the model with a basic root
   */
  public DefaultTreeModel createModel(boolean isOldIssue) {
    latestIssues = Collections.emptyMap();
    treeSummary = new TreeSummary(project, TreeContentKind.ISSUES, isOldIssue);
    summaryNode = new SummaryNode(treeSummary);
    model = new DefaultTreeModel(summaryNode);
    model.setRoot(summaryNode);
    return model;
  }

  public int numberIssues() {
    return summaryNode.getFindingCount();
  }

  private SummaryNode getFilesParent() {
    return summaryNode;
  }

  public void clear() {
    runOnUiThread(project, () -> updateModel(Collections.emptyMap()));
  }

  public void updateModel(Map<VirtualFile, Collection<LiveIssue>> map) {
    latestIssues = map;
    var toRemove = index.getAllFiles().stream().filter(f -> !map.containsKey(f)).toList();
    ApplicationManager.getApplication().assertIsDispatchThread();

    toRemove.forEach(this::removeFile);

    var fileWithIssuesCount = 0;
    issueCount = 0;
    for (var e : map.entrySet()) {
      var fileIssuesCount = setFileIssues(e.getKey(), e.getValue());
      if (fileIssuesCount > 0) {
        issueCount += fileIssuesCount;
        fileWithIssuesCount++;
      }
    }

    treeSummary.refresh(fileWithIssuesCount, issueCount);
    model.nodeChanged(summaryNode);
  }

  public void allowResolvedIssues(boolean allowResolved) {
    if (includeLocallyResolvedIssues != allowResolved) {
      includeLocallyResolvedIssues = allowResolved;
    }
  }

  public int getCountOfDisplayedIssues() {
    return issueCount;
  }

  public void refreshModel(Project project) {
    runOnUiThread(project, () -> updateModel(latestIssues));
    var fileList = new HashSet<>(latestIssues.keySet());
    SonarLintUtils.getService(project, CodeAnalyzerRestarter.class).refreshFiles(fileList);
  }

  private int setFileIssues(VirtualFile file, Iterable<LiveIssue> issues) {
    if (!accept(file)) {
      removeFile(file);
      return 0;
    }

    var filtered = filter(issues);
    if (filtered.isEmpty()) {
      removeFile(file);
      return 0;
    }

    var newFile = false;
    var fNode = index.getFileNode(file);
    if (fNode == null) {
      newFile = true;
      fNode = new FileNode(file, false);
      index.setFileNode(fNode);
    }

    setIssues(fNode, filtered);

    if (newFile) {
      var parent = getFilesParent();
      var idx = parent.insertFileNode(fNode, new FileNodeComparator());
      var newIdx = new int[]{idx};
      model.nodesWereInserted(parent, newIdx);
      model.nodeChanged(parent);
    } else {
      model.nodeStructureChanged(fNode);
    }
    return filtered.size();
  }

  private void removeFile(VirtualFile file) {
    var node = index.getFileNode(file);

    if (node != null) {
      removeFileNode(node);
    }
  }

  private void removeFileNode(FileNode node) {
    index.remove(node.file());
    model.removeNodeFromParent(node);
  }

  private static void setIssues(FileNode node, Iterable<LiveIssue> issuePointers) {
    node.removeAllChildren();

    // 15ms for 500 issues -> to improve?
    var issues = new TreeSet<>(ISSUE_COMPARATOR);

    for (var issue : issuePointers) {
      issues.add(issue);
    }

    for (var issue : issues) {
      var iNode = new IssueNode(issue);
      node.add(iNode);
    }
  }

  private List<LiveIssue> filter(Iterable<LiveIssue> issues) {
    return StreamSupport.stream(issues.spliterator(), false)
      .filter(this::accept)
      .toList();
  }

  private boolean accept(LiveIssue issue) {
    return (!issue.isResolved() && issue.isValid()) || includeLocallyResolvedIssues;
  }

  private static boolean accept(VirtualFile file) {
    return file.isValid();
  }

  public void remove(LiveIssue issue) {
    var fileNode = index.getFileNode(issue.file());
    if (fileNode != null) {
      fileNode.findChildren(child -> Objects.equals(issue.uid(), ((LiveIssue) child).uid()))
        .ifPresent(issueNode -> {
          model.removeNodeFromParent(issueNode);
          if (!fileNode.hasChildren()) {
            removeFileNode(fileNode);
          }
        });
    }
  }

  public Optional<LiveIssue> findIssueByKey(String issueKey) {
    var virtualFile = index.getAllFiles().stream().findFirst();
    if (virtualFile.isPresent()) {
      var fileNode = index.getFileNode(virtualFile.get());
      if (fileNode != null) {
        return fileNode.findChildren(c -> Objects.equals(c.getServerKey(), issueKey) || Objects.equals(c.getId().toString(), issueKey)).map(node -> ((IssueNode) node).issue());
      }
    }
    return Optional.empty();
  }

  public boolean doesIssueExists(String issueKey) {
    var virtualFile = index.getAllFiles().stream().findFirst();
    if (virtualFile.isPresent()) {
      var foundIssue = latestIssues.get(virtualFile.get()).stream().filter(issue -> {
        if (issue.getServerKey() != null && issue.getServerKey().equals(issueKey)) {
          return true;
        } else {
          return issue.getBackendId() != null && issue.getBackendId().toString().equals(issueKey);
        }
      }).findFirst();

      return foundIssue.isPresent();
    }
    return false;
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
      var isResolvedCompare = Comparator.comparing(LiveIssue::isResolved).compare(o1, o2);
      if (isResolvedCompare != 0) {
        return isResolvedCompare;
      }

      var introductionDateOrdering = Ordering.natural().reverse().nullsLast();
      var dateCompare = introductionDateOrdering.compare(o1.getIntroductionDate(), o2.getIntroductionDate());

      if (dateCompare != 0) {
        return dateCompare;
      }

      if (o1.getCleanCodeAttribute() != null && o1.getHighestImpact() != null
        && o2.getCleanCodeAttribute() != null && o2.getHighestImpact() != null) {
        var highestQualityImpactO1 = o1.getHighestImpact();
        var highestQualityImpactO2 = o2.getHighestImpact();
        var impactCompare = Ordering.explicit(IMPACT_ORDER).compare(highestQualityImpactO1, highestQualityImpactO2);
        if (impactCompare != 0) {
          return impactCompare;
        }
      } else {
        var severityCompare = Ordering.explicit(SEVERITY_ORDER).compare(o1.getUserSeverity(), o2.getUserSeverity());
        if (severityCompare != 0) {
          return severityCompare;
        }
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
  public IssueNode getNextIssue(AbstractNode startNode) {
    if (!(startNode instanceof IssueNode)) {
      return firstIssueDown(startNode);
    }

    var next = getNextNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof IssueNode node) {
      return node;
    }

    return firstIssueDown(next);
  }

  @CheckForNull
  public IssueNode getPreviousIssue(AbstractNode startNode) {
    var next = getPreviousNode(startNode);

    if (next == null) {
      // no next node in the entire tree
      return null;
    }

    if (next instanceof IssueNode node) {
      return node;
    }

    return lastIssueDown(next);
  }

  /**
   * Finds the first issue node which is child of a given node.
   */
  @CheckForNull
  private static IssueNode firstIssueDown(AbstractNode node) {
    if (node instanceof IssueNode issueNode) {
      return issueNode;
    }

    if (node.getChildCount() > 0) {
      var firstChild = node.getFirstChild();
      return firstIssueDown((AbstractNode) firstChild);
    }

    return null;
  }

  /**
   * Finds the first issue node which is child of a given node.
   */
  @CheckForNull
  private static IssueNode lastIssueDown(AbstractNode node) {
    if (node instanceof IssueNode issueNode) {
      return issueNode;
    }

    var lastChild = node.getLastChild();

    if (lastChild == null) {
      return null;
    }

    return lastIssueDown((AbstractNode) lastChild);
  }
}
