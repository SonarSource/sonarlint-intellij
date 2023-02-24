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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueTreeModelBuilderTests {
  private final IssueTreeModelBuilder treeBuilder = new IssueTreeModelBuilder();
  private final DefaultTreeModel model = treeBuilder.createModel();

  @Test
  void createModel() {
    var model = treeBuilder.createModel();
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  void testNavigation() throws IOException {
    Map<VirtualFile, Collection<LiveIssue>> data = new HashMap<>();

    // ordering of files: name
    // ordering of issues: creation date (inverse), getSeverity, setRuleName, startLine
    addFile(data, "file1", 2);
    addFile(data, "file2", 2);
    addFile(data, "file3", 2);

    treeBuilder.updateModel(data, "empty");
    var first = treeBuilder.getNextIssue((AbstractNode) model.getRoot());
    assertThat(first).isNotNull();

    var second = treeBuilder.getNextIssue(first);
    assertThat(second).isNotNull();

    var third = treeBuilder.getNextIssue(second);
    assertThat(third).isNotNull();

    assertThat(treeBuilder.getPreviousIssue(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousIssue(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousIssue(first)).isNull();
  }

  @Test
  void testIssueComparator() throws IOException {
    List<LiveIssue> list = new ArrayList<>();

    list.add(mockIssuePointer(100, "rule1", IssueSeverity.MAJOR, null));
    list.add(mockIssuePointer(100, "rule2", IssueSeverity.MAJOR, 1000L));
    list.add(mockIssuePointer(100, "rule3", IssueSeverity.MINOR, 2000L));
    list.add(mockIssuePointer(50, "rule4", IssueSeverity.MINOR, null));
    list.add(mockIssuePointer(100, "rule5", IssueSeverity.MAJOR, null));

    List<LiveIssue> sorted = new ArrayList<>(list);
    sorted.sort(new IssueTreeModelBuilder.IssueComparator());

    // criteria: creation date (most recent, nulls last), getSeverity (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(2), list.get(1), list.get(0), list.get(4), list.get(3));
  }

  private void addFile(Map<VirtualFile, Collection<LiveIssue>> data, String fileName, int numIssues) throws IOException {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.isValid()).thenReturn(true);

    List<LiveIssue> issueList = new LinkedList<>();

    for (var i = 0; i < numIssues; i++) {
      issueList.add(mockIssuePointer(i, "rule" + i, IssueSeverity.MAJOR, (long) i));
    }

    data.put(file, issueList);
  }

  private static LiveIssue mockIssuePointer(int startOffset, String rule, IssueSeverity severity, @Nullable Long introductionDate) throws IOException {
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);

    var issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getSeverity()).thenReturn(severity);
    when(issue.getType()).thenReturn(RuleType.BUG);

    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);

    var ip = new LiveIssue(issue, psiFile, Collections.emptyList());
    ip.setIntroductionDate(introductionDate);
    return ip;
  }

}
