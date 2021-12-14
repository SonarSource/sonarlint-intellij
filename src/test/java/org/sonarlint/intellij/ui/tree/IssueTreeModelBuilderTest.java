/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.junit.Test;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueTreeModelBuilderTest {
  private IssueTreeModelBuilder treeBuilder = new IssueTreeModelBuilder();
  private DefaultTreeModel model  = treeBuilder.createModel();

  @Test
  public void createModel() {
    var model = treeBuilder.createModel();
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  public void testNavigation() {
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
    assertThat(second).isNotNull();

    assertThat(treeBuilder.getPreviousIssue(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousIssue(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousIssue(first)).isNull();
  }

  @Test
  public void testIssueComparator() {
    List<LiveIssue> list = new ArrayList<>();

    list.add(mockIssuePointer("f1", 100, "rule1", "MAJOR", null));
    list.add(mockIssuePointer("f1", 100, "rule2", "MAJOR", 1000L));
    list.add(mockIssuePointer("f1", 100, "rule3", "MINOR", 2000L));
    list.add(mockIssuePointer("f1", 50, "rule4", "MINOR", null));
    list.add(mockIssuePointer("f1", 100, "rule5", "MAJOR", null));

    List<LiveIssue> sorted = new ArrayList<>(list);
    sorted.sort(new IssueTreeModelBuilder.IssueComparator());

    // criteria: creation date (most recent, nulls last), getSeverity (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(2), list.get(1), list.get(0), list.get(4), list.get(3));
  }

  private void addFile(Map<VirtualFile, Collection<LiveIssue>> data, String fileName, int numIssues) {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.isValid()).thenReturn(true);

    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    List<LiveIssue> issueList = new LinkedList<>();

    for (var i = 0; i < numIssues; i++) {
      issueList.add(mockIssuePointer(fileName, i, "rule" + i, "MAJOR", (long) i));
    }

    data.put(file, issueList);
  }

  private static LiveIssue mockIssuePointer(String path, int startOffset, String rule, String severity, @Nullable Long creationDate) {
    var issue = mock(Issue.class);
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    var f = mockFile(path);
    when(issue.getInputFile()).thenReturn(f);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getSeverity()).thenReturn(severity);
    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);
    var ip = new LiveIssue(issue, psiFile, Collections.emptyList());
    ip.setCreationDate(creationDate);
    return ip;
  }

  private static ClientInputFile mockFile(String path) {
    var file = mock(ClientInputFile.class);
    when(file.getPath()).thenReturn(path);
    when(file.getCharset()).thenReturn(Charset.defaultCharset());
    when(file.isTest()).thenReturn(false);
    return file;
  }

}
