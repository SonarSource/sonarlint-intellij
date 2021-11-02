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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IssueManagerTest extends AbstractSonarLintLightTests {
  private IssueManager underTest;
  private LiveIssueCache cache = mock(LiveIssueCache.class);
  private VirtualFile file1 = mock(VirtualFile.class);
  private Document document = mock(Document.class);
  private LiveIssue issue1;

  @Captor
  private ArgumentCaptor<Collection<LiveIssue>> issueCollectionCaptor;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file1.getPath()).thenReturn("file1");

    underTest = new IssueManager(getProject(), cache);

    issue1 = createRangeStoredIssue(1, "issue 1", 10);

    when(cache.contains(file1)).thenReturn(true);
    when(cache.getLive(file1)).thenReturn(singletonList(issue1));
  }

  @Test
  public void should_return_file_issues() {
    assertThat(underTest.getForFile(file1)).containsExactly(issue1);
  }

  @Test
  public void testTracking() {
    // tracking based on setRuleKey / line number
    var previousIssue = createRangeStoredIssue(1, "issue 1", 10);
    previousIssue.setCreationDate(1000L);
    previousIssue.setSeverity("old");
    previousIssue.setType("old");

    var rawIssue = createRangeStoredIssue(1, "issue 1", 10);
    rawIssue.setCreationDate(2000L);
    rawIssue.setSeverity("severity");
    rawIssue.setType("type");

    var previousIssues = new ArrayList<>(List.of(previousIssue));

    var trackedIssue = underTest.trackSingleIssue(file1, previousIssues, rawIssue);

    // matched issues are removed from the list
    assertThat(previousIssues).isEmpty();

    assertThat(trackedIssue.getCreationDate()).isEqualTo(1000);
    assertThat(trackedIssue.getSeverity()).isEqualTo("severity");
    assertThat(trackedIssue.getType()).isEqualTo("type");
  }

  @Test
  public void testTracking_by_checksum() {
    // tracking based on checksum
    issue1.setCreationDate(1000L);

    // line is different
    var i2 = createRangeStoredIssue(1, "issue 1", 11);
    i2.setCreationDate(2000L);

    var previousIssues = new ArrayList<>(List.of(issue1));
    var trackedIssue = underTest.trackSingleIssue(file1, previousIssues, i2);

    // matched issues are removed from the list
    assertThat(previousIssues).isEmpty();

    assertThat(trackedIssue.getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void should_copy_server_issue_on_match() {
    var serverIssueKey = "dummyServerIssueKey";
    issue1.setSeverity("localSeverity");
    issue1.setType("localType");
    assertThat(issue1.getServerIssueKey()).isNull();

    var serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setSeverity("serverSeverity");
    // old SQ servers don't give type
    serverIssue.setType(null);
    underTest.matchWithServerIssues(file1, singletonList(serverIssue));

    // issue1 has been changed
    assertThat(issue1.getServerIssueKey()).isEqualTo(serverIssueKey);
    assertThat(issue1.getSeverity()).isEqualTo("serverSeverity");
    assertThat(issue1.getType()).isEqualTo("localType");
  }


  @Test
  public void should_track_server_issue_based_on_rule_key() {
    var serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    var newAssignee = "newAssignee";
    serverIssue.setAssignee(newAssignee);
    serverIssue.setResolved(true);

    underTest.matchWithServerIssues(file1, Collections.singleton(serverIssue));

    assertThat(issue1.isResolved()).isTrue();
    assertThat(issue1.getAssignee()).isEqualTo(newAssignee);
  }

  @Test
  public void should_track_server_issue_based_on_issue_key_even_if_no_other_attributes_matches() {
    var serverIssueKey = "dummyServerIssueKey";
    issue1.setServerIssueKey(serverIssueKey);
    var localLine = issue1.getLine();

    var serverIssue = createRangeStoredIssue(2, "server issue", localLine + 100);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setResolved(true);
    serverIssue.setSeverity("sev");
    serverIssue.setType("type");
    var newAssignee = "newAssignee";
    serverIssue.setAssignee(newAssignee);
    underTest.matchWithServerIssues(file1, singletonList(serverIssue));

    // the local issue is preserved ...
    assertThat(issue1.getLine()).isEqualTo(localLine);
    // ... + the change from the server is copied
    assertThat(issue1.isResolved()).isTrue();
    assertThat(issue1.getSeverity()).isEqualTo("sev");
    assertThat(issue1.getType()).isEqualTo("type");
    assertThat(issue1.getAssignee()).isEqualTo(newAssignee);
  }

  @Test
  public void should_ignore_server_issue_if_not_matched() {
    var serverIssue = createRangeStoredIssue(2, "server issue", issue1.getLine() + 100);
    serverIssue.setServerIssueKey("dummyServerIssueKey");
    underTest.matchWithServerIssues(file1, singletonList(serverIssue));

    assertThat(issue1.getServerIssueKey()).isNull();
  }

  @Test
  public void should_drop_server_issue_reference_if_gone() {
    issue1.setCreationDate(1000L);
    issue1.setServerIssueKey("dummyServerIssueKey");
    issue1.setSeverity("sev");

    underTest.matchWithServerIssues(file1, Collections.emptyList());

    assertThat(issue1.getServerIssueKey()).isNull();

    // keep old creation date and severity
    assertThat(issue1.getCreationDate()).isEqualTo(1000L);
    assertThat(issue1.getSeverity()).isEqualTo("sev");
  }

  @Test
  public void unknown_file() {
    var unknownFile = mock(VirtualFile.class);
    when(cache.getLive(unknownFile)).thenReturn(null);
    assertThat(underTest.getForFileOrNull(unknownFile)).isNull();
    assertThat(underTest.getForFile(unknownFile)).isEmpty();
  }

  @Test
  public void should_preserve_creation_date_of_leaked_issues_in_connected_mode() {
    var creationDate = 1L;
    issue1.setCreationDate(creationDate);

    underTest.matchWithServerIssues(file1, Collections.emptyList());

    assertThat(issue1.getCreationDate()).isEqualTo(creationDate);
  }

  @Test
  public void testClear() {
    underTest.clearAllIssuesForAllFiles();
    verify(cache).clear();
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
