/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.persistence.IssuePersistence;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IssueManagerTest extends SonarTest {
  private IssueManager manager = mock(IssueManager.class);
  private LiveIssueCache cache = mock(LiveIssueCache.class);
  private VirtualFile file1 = mock(VirtualFile.class);
  private Document document = mock(Document.class);
  private IssuePersistence store = mock(IssuePersistence.class);
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);
  private LiveIssue issue1;

  @Captor
  private ArgumentCaptor<Collection<LiveIssue>> issueCollectionCaptor;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file1.getPath()).thenReturn("file1");

    when(project.getBasePath()).thenReturn("");
    manager = new IssueManager(appUtils, project, cache, store);

    issue1 = createRangeStoredIssue(1, "issue 1", 10);

    when(cache.contains(file1)).thenReturn(true);
    when(cache.getLive(file1)).thenReturn(Collections.singletonList(issue1));
  }

  @Test
  public void should_return_file_issues() {
    assertThat(manager.getForFile(file1)).containsExactly(issue1);
  }

  @Test
  public void testTracking() {
    // tracking based on setRuleKey / line number
    LiveIssue i1 = createRangeStoredIssue(1, "issue 1", 10);
    i1.setCreationDate(1000L);
    i1.setSeverity("old");
    i1.setType("old");
    when(cache.contains(file1)).thenReturn(true);
    when(cache.getLive(file1)).thenReturn(Collections.singletonList(i1));

    LiveIssue i2 = createRangeStoredIssue(1, "issue 1", 10);
    i2.setCreationDate(2000L);
    i2.setSeverity("severity");
    i2.setType("type");

    manager.store(file1, Collections.singletonList(i2));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();

    assertThat(fileIssues).hasSize(1);
    assertThat(fileIssues.iterator().next().getCreationDate()).isEqualTo(1000);
    assertThat(fileIssues.iterator().next().getSeverity()).isEqualTo("severity");
    assertThat(fileIssues.iterator().next().getType()).isEqualTo("type");
  }

  @Test
  public void testTracking_checksum() {
    // tracking based on checksum
    issue1.setCreationDate(1000L);

    LiveIssue i2 = createRangeStoredIssue(1, "issue 1", 11);
    i2.setCreationDate(2000L);
    manager.store(file1, Collections.singletonList(i2));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> issues = issueCollectionCaptor.getValue();

    assertThat(issues).hasSize(1);
    assertThat(issues.iterator().next().getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void should_copy_server_issue_on_match() {
    String serverIssueKey = "dummyServerIssueKey";

    issue1.setSeverity("localSeverity");
    issue1.setType("localType");

    LiveIssue serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setSeverity("serverSeverity");
    // old SQ servers don't give type
    serverIssue.setType(null);
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();
    assertThat(fileIssues).hasSize(1);

    LiveIssue issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(issue1.uid());
    assertThat(issuePointer.getServerIssueKey()).isEqualTo(serverIssueKey);
    assertThat(issuePointer.getSeverity()).isEqualTo("serverSeverity");
    assertThat(issuePointer.getType()).isEqualTo("localType");
  }

  @Test
  public void should_preserve_server_issue_if_moved_locally() {
    String serverIssueKey = "dummyServerIssueKey";
    issue1.setServerIssueKey(serverIssueKey);

    LiveIssue serverIssue = createRangeStoredIssue(2, "server issue", issue1.getLine() + 100);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setResolved(true);
    serverIssue.setSeverity("sev");
    serverIssue.setType("type");
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();

    assertThat(fileIssues).hasSize(1);

    LiveIssue issuePointer = fileIssues.iterator().next();
    // the local issue is preserved ...
    assertThat(issuePointer.uid()).isEqualTo(issue1.uid());
    assertThat(issuePointer.getLine()).isEqualTo(issue1.getLine());
    // ... + the change from the server is copied
    assertThat(issuePointer.isResolved()).isTrue();
    assertThat(issuePointer.getSeverity()).isEqualTo("sev");
    assertThat(issuePointer.getType()).isEqualTo("type");
  }

  @Test
  public void should_ignore_server_issue_if_not_matched() {
    LiveIssue serverIssue = createRangeStoredIssue(2, "server issue", issue1.getLine() + 100);
    serverIssue.setServerIssueKey("dummyServerIssueKey");
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();

    assertThat(fileIssues).hasSize(1);
    assertThat(manager.getForFileOrNull(file1)).containsExactly(fileIssues.iterator().next());

    LiveIssue issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(issue1.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();
  }

  @Test
  public void should_drop_server_issue_reference_if_gone() {
    issue1.setCreationDate(1000L);
    issue1.setServerIssueKey("dummyServerIssueKey");
    issue1.setSeverity("sev");

    manager.matchWithServerIssues(file1, Collections.emptyList());

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();
    assertThat(fileIssues).hasSize(1);

    LiveIssue issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(issue1.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();

    // keep old creation date and severity
    assertThat(issuePointer.getCreationDate()).isEqualTo(1000L);
    assertThat(issuePointer.getSeverity()).isEqualTo("sev");
  }

  @Test
  public void unknown_file() {
    VirtualFile unknownFile = mock(VirtualFile.class);
    when(cache.getLive(unknownFile)).thenReturn(null);
    assertThat(manager.getForFileOrNull(unknownFile)).isNull();
    assertThat(manager.getForFile(unknownFile)).isEmpty();
  }

  @Test
  public void should_update_server_issue() {
    issue1.setServerIssueKey("dummyServerIssueKey");

    LiveIssue serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    String newAssignee = "newAssignee";
    serverIssue.setAssignee(newAssignee);
    serverIssue.setResolved(true);

    manager.matchWithServerIssues(file1, Collections.singleton(serverIssue));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();
    assertThat(fileIssues).hasSize(1);

    LiveIssue issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.isResolved()).isTrue();
    assertThat(issuePointer.getAssignee()).isEqualTo(newAssignee);
  }

  @Test
  public void should_preserve_creation_date_of_leaked_issues_in_connected_mode() {
    Long creationDate = 1L;
    issue1.setCreationDate(creationDate);

    manager.matchWithServerIssues(file1, Collections.emptyList());

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());
    Collection<LiveIssue> fileIssues = issueCollectionCaptor.getValue();
    assertThat(fileIssues).hasSize(1);
    assertThat(fileIssues.iterator().next().getCreationDate()).isEqualTo(creationDate);
  }

  @Test
  public void testClear() {
    manager.clear();
    verify(cache).clear();
  }

  private LiveIssue createRangeStoredIssue(int id, String rangeContent, int line) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    when(issue.getStartLine()).thenReturn(line);
    RangeMarker range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    PsiFile psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    return new LiveIssue(issue, psiFile, range, Collections.emptyList());
  }
}
