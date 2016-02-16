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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarsource.sonarlint.core.client.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.Issue;

import javax.swing.tree.DefaultTreeModel;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeModelBuilderTest {
  private TreeModelBuilder treeBuilder;
  private DefaultTreeModel model;

  @Before
  public void setUp() {
    treeBuilder = new TreeModelBuilder();
    model = treeBuilder.createModel();
  }

  @Test
  public void createModel() {
    DefaultTreeModel model = treeBuilder.createModel();
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  public void testNavigation() {
    Map<VirtualFile, Collection<IssuePointer>> data = new HashMap<>();

    // ordering of files: name
    // ordering of issues: creation date (inverse), severity, ruleName, startLine
    addFile(data, "file1", 2);
    addFile(data, "file2", 2);
    addFile(data, "file3", 2);

    treeBuilder.updateModel(data, null);
    IssueNode first = treeBuilder.getNextIssue((AbstractNode<?>) model.getRoot());
    assertNode(first, "file1", 1);

    IssueNode second = treeBuilder.getNextIssue(first);
    assertNode(second, "file1", 0);

    IssueNode third = treeBuilder.getNextIssue(second);
    assertNode(third, "file2", 1);

    assertThat(treeBuilder.getPreviousIssue(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousIssue(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousIssue(first)).isNull();
  }

  private void assertNode(IssueNode node, String file, int number) {
    assertThat(node).isNotNull();
    assertThat(node.issue().issue().getInputFile().getPath()).isEqualTo(Paths.get(file));
    assertThat(node.issue().issue().getRuleName()).isEqualTo("rule" + number);
  }

  private void addFile(Map<VirtualFile, Collection<IssuePointer>> data, String fileName, int numIssues) {
    VirtualFile file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.isValid()).thenReturn(true);

    PsiFile psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    List<IssuePointer> issueList = new LinkedList<>();

    for (int i = 0; i < numIssues; i++) {
      Issue issue = mock(Issue.class);
      when(issue.getStartLine()).thenReturn(i);
      ClientInputFile f = mockFile(fileName);
      when(issue.getInputFile()).thenReturn(f);
      when(issue.getRuleKey()).thenReturn("rule" + i);
      when(issue.getRuleName()).thenReturn("rule" + i);
      when(issue.getSeverity()).thenReturn("MAJOR");
      IssuePointer ip = new IssuePointer(issue,psiFile);
      ip.setCreationDate(i);
      issueList.add(ip);
    }

    data.put(file, issueList);
  }

  private static ClientInputFile mockFile(String path) {
    ClientInputFile file = mock(ClientInputFile.class);
    when(file.getPath()).thenReturn(Paths.get(path));
    when(file.getCharset()).thenReturn(Charset.defaultCharset());
    when(file.isTest()).thenReturn(false);
    return file;
  }

}
