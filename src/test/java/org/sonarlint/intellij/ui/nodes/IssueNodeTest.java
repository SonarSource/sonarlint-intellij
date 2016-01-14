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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.junit.Before;
import org.junit.Test;
import org.sonar.runner.api.Issue;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.util.ResourceLoader;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IssueNodeTest  extends LightPlatformCodeInsightFixtureTestCase {
  private IssueNode node;
  private IssuePointer issue;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    issue = mock(IssuePointer.class);
    node = new IssueNode(issue);
  }

  @Test
  public void testCount() {
    assertThat(node.getFileCount()).isZero();
    assertThat(node.getIssueCount()).isEqualTo(1);
  }

  @Test
  public void testGetIssue() {
    assertThat(node.issue()).isEqualTo(issue);
  }

  @Test
  public void testRender() throws IOException {
    VirtualFile testFile = myFixture.addFileToProject("testFile.java", "dummy text\nsecond line").getVirtualFile();
    Document doc = FileDocumentManager.getInstance().getDocument(testFile);
    RangeMarker range = doc.createRangeMarker(15, 18);

    issue = createTestIssue(range, "MAJOR");
    node = new IssueNode(issue);

    ColoredTreeCellRenderer renderer = mock(ColoredTreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(1, 4) ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
    verify(renderer).setIcon(ResourceLoader.getSeverityIcon("MAJOR"));
  }

  @Test
  public void testRenderWithoutRange() throws IOException {
    VirtualFile testFile = myFixture.addFileToProject("testFile.java", "dummy text\nsecond line").getVirtualFile();
    Document doc = FileDocumentManager.getInstance().getDocument(testFile);

    issue = createTestIssue(doc, "MAJOR");
    node = new IssueNode(issue);

    ColoredTreeCellRenderer renderer = mock(ColoredTreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(0, 0) ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("rule");
    verify(renderer).setIcon(ResourceLoader.getSeverityIcon("MAJOR"));
  }

  private IssuePointer createTestIssue(RangeMarker range, String severity) {
    Issue issue = Issue.builder()
      .setSeverity(severity)
      .setRuleName("rule")
      .build();
    PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(range.getDocument());

    return new IssuePointer(issue, file, range);
  }

  private IssuePointer createTestIssue(Document doc, String severity) {
    Issue issue = Issue.builder()
      .setSeverity(severity)
      .setRuleName("rule")
      .build();
    PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);

    return new IssuePointer(issue, file, null);
  }
}
