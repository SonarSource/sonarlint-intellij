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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.persistence.IssueCache;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IssueManagerTest extends SonarTest {
  private IssueManager manager;

  @Mock
  private IssueCache cache;
  @Mock
  private VirtualFile file1;
  @Mock
  private VirtualFile file2;
  @Mock
  private Document document;

  private LocalIssuePointer issue1;
  private LocalIssuePointer issue2;

  @Captor
  private ArgumentCaptor<Collection<LocalIssuePointer>> issueCollectionCaptor;

  @Before
  public void setUp() {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file2.isValid()).thenReturn(true);
    manager = new IssueManager(project, cache);

    issue1 = createRangeStoredIssue(1, "issue 1", 10);
    issue2 = createRangeStoredIssue(2, "issue 2", 10);

    when(cache.read(file1)).thenReturn(Collections.singletonList(issue1));
    when(cache.read(file2)).thenReturn(Collections.singletonList(issue2));
  }

  @Test
  public void should_return_file_issues() {
    assertThat(manager.getForFile(file1)).containsExactly(issue1);
    assertThat(manager.getForFile(file2)).containsExactly(issue2);
  }

  @Test
  public void testTracking() {
    // tracking based on ruleKey / line number
    manager.clear();
    LocalIssuePointer i1 = createRangeStoredIssue(1, "issue 1", 10);
    i1.setCreationDate(1000L);
    when(cache.read(file1)).thenReturn(Collections.singletonList(i1));

    LocalIssuePointer i2 = createRangeStoredIssue(1, "issue 1", 10);
    i2.setCreationDate(2000L);
    manager.store(file1, Collections.singletonList(i2));

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);
    assertThat(fileIssues.iterator().next().getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void testTracking_checksum() {
    // tracking based on checksum
    LocalIssuePointer i1 = createRangeStoredIssue(1, "issue 1", 10);
    i1.setCreationDate(1000L);
    when(cache.read(file1)).thenReturn(Collections.singletonList(i1));
    when(cache.contains(file1)).thenReturn(true);

    LocalIssuePointer i2 = createRangeStoredIssue(1, "issue 1", 11);
    i2.setCreationDate(2000L);
    manager.store(file1, Collections.singletonList(i2));

    verify(cache).save(eq(file1), issueCollectionCaptor.capture());

    Collection<LocalIssuePointer> issues = issueCollectionCaptor.getValue();
    assertThat(issues).hasSize(1);
    assertThat(issues.iterator().next().getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void testTracking_should_copy_server_issue_on_match() {
    String serverIssueKey = "dummyServerIssueKey";

    LocalIssuePointer localIssue = createRangeStoredIssue(1, "issue 1", 10);
    when(cache.read(file1)).thenReturn(Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setServerIssueKey(serverIssueKey);
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(localIssue.uid());
    assertThat(issuePointer.getServerIssueKey()).isEqualTo(serverIssueKey);
  }

  @Test
  public void testTracking_should_preserve_server_issue_if_moved_locally() {
    String serverIssueKey = "dummyServerIssueKey";

    LocalIssuePointer localIssue = createRangeStoredIssue(1, "local issue", 10);
    localIssue.setServerIssueKey(serverIssueKey);
    when(cache.read(file1)).thenReturn(Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(2, "server issue", localIssue.getLine() + 100);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setResolved(true);
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    // the local issue is preserved ...
    assertThat(issuePointer.uid()).isEqualTo(localIssue.uid());
    assertThat(issuePointer.getLine()).isEqualTo(localIssue.getLine());
    // ... + the change from the server is copied
    assertThat(issuePointer.isResolved()).isTrue();
  }

  @Test
  public void testTracking_should_ignore_server_issue_if_not_matched() {
    LocalIssuePointer localIssue = createRangeStoredIssue(1, "local issue", 10);
    when(cache.read(file1)).thenReturn(Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(2, "server issue", localIssue.getLine() + 100);
    serverIssue.setServerIssueKey("dummyServerIssueKey");
    manager.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(localIssue.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();
  }

  @Test
  public void testTracking_should_drop_server_issue_reference_if_gone() {
    LocalIssuePointer issue = createRangeStoredIssue(1, "issue 1", 10);
    issue.setServerIssueKey("dummyServerIssueKey");
    when(cache.read(file1)).thenReturn(Collections.singletonList(issue));

    manager.matchWithServerIssues(file1, Collections.emptyList());

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(issue.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();
  }

  @Test
  public void testTracking_should_update_server_issue() {
    LocalIssuePointer issue = createRangeStoredIssue(1, "issue 1", 10);
    issue.setServerIssueKey("dummyServerIssueKey");
    when(cache.read(file1)).thenReturn(Collections.singletonList(issue));

    issue.setResolved(true);
    String newAssignee = "newAssignee";
    issue.setAssignee(newAssignee);
    when(cache.read(file1)).thenReturn(Collections.singletonList(issue));

    Collection<LocalIssuePointer> fileIssues = manager.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.isResolved()).isTrue();
    assertThat(issuePointer.getAssignee()).isEqualTo(newAssignee);
  }

  @Test
  public void testClear() {
    manager.clear();
    verify(cache).clear();
  }

  private LocalIssuePointer createRangeStoredIssue(int id, String rangeContent, int line) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    when(issue.getStartLine()).thenReturn(line);
    RangeMarker range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    PsiFile psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    return new LocalIssuePointer(issue, psiFile, range);
  }
}
