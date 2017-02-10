/*
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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import icons.SonarLintIcons;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IssueNodeTest {
  private IssueNode node;
  private TreeCellRenderer renderer;

  @Before
  public void setUp() {
    renderer = mock(TreeCellRenderer.class);
  }

  @Test
  public void testAge() {
    LiveIssue i = createIssue(System.currentTimeMillis(), "rule");
    node = new IssueNode(i);
    node.render(renderer);

    verify(renderer).append("few seconds ago", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
  }

  @Test
  public void testHoursAndSeverity() throws IOException {
    LiveIssue i = createIssue(System.currentTimeMillis() - 3600 * 1000, "rule");
    node = new IssueNode(i);
    node.render(renderer);

    verify(renderer).append("1 hour ago", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
    verify(renderer).setIcon(SonarLintIcons.severity("MAJOR"));
  }

  @Test
  public void testInvalidIssue() {
    LiveIssue i = createIssue(System.currentTimeMillis(), "rule");
    when(i.isValid()).thenReturn(false);
    node = new IssueNode(i);
    node.render(renderer);

    verify(renderer).append("rule", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Test
  public void testCount() {
    LiveIssue i = createIssue(System.currentTimeMillis(), "rule");
    node = new IssueNode(i);
    assertThat(node.getFileCount()).isZero();
    assertThat(node.getIssueCount()).isEqualTo(1);
    assertThat(node.issue()).isEqualTo(i);
  }

  private static LiveIssue createIssue(long date, String message) {
    PsiFile file = mock(PsiFile.class);
    when(file.isValid()).thenReturn(true);
    Issue issue = mock(Issue.class);
    when(issue.getMessage()).thenReturn(message);
    when(issue.getSeverity()).thenReturn("MAJOR");
    LiveIssue issuePointer = new LiveIssue(issue, file);
    issuePointer.setCreationDate(date);
    return issuePointer;
  }
}
