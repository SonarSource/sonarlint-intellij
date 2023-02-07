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
package org.sonarlint.intellij.finding.tracking;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerFindingTrackerTest extends AbstractSonarLintLightTests {
  private ServerFindingTracker underTest;
  private VirtualFile file1 = mock(VirtualFile.class);
  private Document document = mock(Document.class);
  private LiveIssue issue1;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file1.getPath()).thenReturn("file1");

    underTest = new ServerFindingTracker();

    issue1 = createRangeStoredIssue(1, "issue 1", 10);
  }


  @Test
  public void should_copy_server_issue_on_match() {
    var serverIssueKey = "dummyServerIssueKey";
    issue1.setSeverity(IssueSeverity.INFO);
    issue1.setType(RuleType.VULNERABILITY);
    assertThat(issue1.getServerFindingKey()).isNull();

    var serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setServerFindingKey(serverIssueKey);
    serverIssue.setSeverity(IssueSeverity.CRITICAL);
    serverIssue.setType(RuleType.BUG);
    underTest.matchLocalWithServerFindings(List.of(serverIssue), List.of(issue1));

    // issue1 has been changed
    assertThat(issue1.getServerFindingKey()).isEqualTo(serverIssueKey);
    assertThat(issue1.getUserSeverity()).isEqualTo(IssueSeverity.CRITICAL);
    assertThat(issue1.getType()).isEqualTo(RuleType.BUG);
  }

  @Test
  public void should_track_server_issue_based_on_rule_key() {
    var serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setResolved(true);

    underTest.matchLocalWithServerFindings(Collections.singleton(serverIssue), List.of(issue1));

    assertThat(issue1.isResolved()).isTrue();
  }

  @Test
  public void should_track_server_issue_based_on_issue_key_even_if_no_other_attributes_matches() {
    var serverIssueKey = "dummyServerIssueKey";
    issue1.setServerFindingKey(serverIssueKey);
    var localLine = issue1.getLine();

    var serverIssue = createRangeStoredIssue(2, "server issue", localLine + 100);
    serverIssue.setServerFindingKey(serverIssueKey);
    serverIssue.setResolved(true);
    serverIssue.setSeverity(IssueSeverity.MAJOR);
    serverIssue.setType(RuleType.BUG);
    underTest.matchLocalWithServerFindings(List.of(serverIssue), List.of(issue1));

    // the local issue is preserved ...
    assertThat(issue1.getLine()).isEqualTo(localLine);
    // ... + the change from the server is copied
    assertThat(issue1.isResolved()).isTrue();
    assertThat(issue1.getUserSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(issue1.getType()).isEqualTo(RuleType.BUG);
  }

  @Test
  public void should_ignore_server_issue_if_not_matched() {
    var serverIssue = createRangeStoredIssue(2, "server issue", issue1.getLine() + 100);
    serverIssue.setServerFindingKey("dummyServerIssueKey");
    underTest.matchLocalWithServerFindings(List.of(serverIssue), List.of(issue1));

    assertThat(issue1.getServerFindingKey()).isNull();
  }

  @Test
  public void should_drop_server_issue_reference_if_gone() {
    issue1.setCreationDate(1000L);
    issue1.setServerFindingKey("dummyServerIssueKey");
    issue1.setSeverity(IssueSeverity.BLOCKER);

    underTest.matchLocalWithServerFindings(Collections.emptyList(), List.of(issue1));

    assertThat(issue1.getServerFindingKey()).isNull();

    // keep old creation date and severity
    assertThat(issue1.getCreationDate()).isEqualTo(1000L);
    assertThat(issue1.getUserSeverity()).isEqualTo(IssueSeverity.BLOCKER);
  }

  @Test
  public void should_preserve_creation_date_of_leaked_issues_in_connected_mode() {
    var creationDate = 1L;
    issue1.setCreationDate(creationDate);

    underTest.matchLocalWithServerFindings(Collections.emptyList(), List.of(issue1));

    assertThat(issue1.getCreationDate()).isEqualTo(creationDate);
  }

  private LiveIssue createRangeStoredIssue(int id, String rangeContent, int line) {
    var issue = SonarLintTestUtils.createIssue(id);
    when(issue.getStartLine()).thenReturn(line);
    var range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    return new LiveIssue(issue, psiFile, range, null, Collections.emptyList());
  }
}