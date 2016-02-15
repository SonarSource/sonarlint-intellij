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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarsource.sonarlint.core.IssueListener;

import javax.annotation.Nullable;
import javax.swing.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IssueNodeTest {
  private IssueNode node;
  private ColoredTreeCellRenderer renderer;

  @Before
  public void setUp() {
    renderer = mock(ColoredTreeCellRenderer.class);
  }

  @Test
  public void testAge() {
    IssuePointer i = createIssue(System.currentTimeMillis(), "rule");
    node = new IssueNode(i);
    node.render(renderer);

    verify(renderer).append("few seconds ago", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
  }

  @Test
  public void testHoursAndSeverity() throws IOException {
    IssuePointer i = createIssue(System.currentTimeMillis()-3600*1000, "rule");
    i.issue().setSeverity("MAJOR");
    node = new IssueNode(i);
    node.render(renderer);

    verify(renderer).append("1 hour ago", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
    verify(renderer).setIcon(ResourceLoader.getSeverityIcon("MAJOR"));
  }

  @Test
  public void testCount() {
    IssuePointer i = createIssue(System.currentTimeMillis(), "rule");
    node = new IssueNode(i);
    assertThat(node.getFileCount()).isZero();
    assertThat(node.getIssueCount()).isEqualTo(1);
    assertThat(node.issue()).isEqualTo(i);
  }

  private static IssuePointer createIssue(long date, String ruleName) {
    PsiFile file = mock(PsiFile.class);
    IssueListener.Issue i = new IssueListener.Issue();
    i.setRuleName(ruleName);
    IssuePointer issue = new IssuePointer(i, file);
    issue.setCreationDate(date);
    return issue;
  }
}
