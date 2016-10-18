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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueStoreTest extends SonarTest {
  private IssueStore store;

  private VirtualFile file1;
  private VirtualFile file2;

  private LocalIssuePointer issue1;
  private LocalIssuePointer issue2;
  private Document document = mock(Document.class);

  @Before
  public void setUp() {
    super.setUp();
    file1 = mock(VirtualFile.class);
    file2 = mock(VirtualFile.class);
    when(file1.isValid()).thenReturn(true);
    when(file2.isValid()).thenReturn(true);
    store = new IssueStore(project);

    issue1 = createRangeStoredIssue(1, "issue 1", 10);
    issue2 = createRangeStoredIssue(2, "issue 2", 10);

    store.store(file1, Collections.singletonList(issue1));
    store.store(file2, Collections.singletonList(issue2));
  }

  @Test
  public void testStore() {
    assertThat(store.getForFile(file1)).containsExactly(issue1);
    assertThat(store.getForFile(file2)).containsExactly(issue2);
  }

  @Test
  public void testTracking() {
    // tracking based on ruleKey / line number
    store.clear();
    LocalIssuePointer i1 = createRangeStoredIssue(1, "issue 1", 10);
    i1.setCreationDate(1000L);
    store.store(file1, Collections.singletonList(i1));

    LocalIssuePointer i2 = createRangeStoredIssue(1, "issue 1", 10);
    i2.setCreationDate(2000L);
    store.store(file1, Collections.singletonList(i2));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);
    assertThat(fileIssues.iterator().next().getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void testTrackingChecksum() {
    // tracking based on checksum
    store.clear();
    LocalIssuePointer i1 = createRangeStoredIssue(1, "issue 1", 10);
    i1.setCreationDate(1000L);
    store.store(file1, Collections.singletonList(i1));

    LocalIssuePointer i2 = createRangeStoredIssue(1, "issue 1", 11);
    i2.setCreationDate(2000L);
    store.store(file1, Collections.singletonList(i2));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);
    assertThat(fileIssues.iterator().next().getCreationDate()).isEqualTo(1000);
  }

  @Test
  public void testTracking_should_copy_server_issue_on_match() {
    store.clear();

    String serverIssueKey = "dummyServerIssueKey";

    LocalIssuePointer localIssue = createRangeStoredIssue(1, "issue 1", 10);
    store.store(file1, Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(1, "issue 1", 10);
    serverIssue.setServerIssueKey(serverIssueKey);
    store.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(localIssue.uid());
    assertThat(issuePointer.getServerIssueKey()).isEqualTo(serverIssueKey);
  }

  @Test
  public void testTracking_should_preserve_server_issue_if_moved_locally() {
    store.clear();

    String serverIssueKey = "dummyServerIssueKey";

    LocalIssuePointer localIssue = createRangeStoredIssue(1, "local issue", 10);
    localIssue.setServerIssueKey(serverIssueKey);
    store.store(file1, Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(2, "server issue", localIssue.getLine() + 100);
    serverIssue.setServerIssueKey(serverIssueKey);
    serverIssue.setResolved(true);
    store.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
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
    store.clear();

    LocalIssuePointer localIssue = createRangeStoredIssue(1, "local issue", 10);
    store.store(file1, Collections.singletonList(localIssue));

    LocalIssuePointer serverIssue = createRangeStoredIssue(2, "server issue", localIssue.getLine() + 100);
    serverIssue.setServerIssueKey("dummyServerIssueKey");
    store.matchWithServerIssues(file1, Collections.singletonList(serverIssue));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(localIssue.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();
  }

  @Test
  public void testTracking_should_drop_server_issue_reference_if_gone() {
    store.clear();

    LocalIssuePointer issue = createRangeStoredIssue(1, "issue 1", 10);
    issue.setServerIssueKey("dummyServerIssueKey");
    store.store(file1, Collections.singletonList(issue));

    store.matchWithServerIssues(file1, Collections.emptyList());

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.uid()).isEqualTo(issue.uid());
    assertThat(issuePointer.getServerIssueKey()).isNull();
  }

  @Test
  public void testTracking_should_update_server_issue() {
    store.clear();

    LocalIssuePointer issue = createRangeStoredIssue(1, "issue 1", 10);
    issue.setServerIssueKey("dummyServerIssueKey");
    store.store(file1, Collections.singletonList(issue));

    issue.setResolved(true);
    String newAssignee = "newAssignee";
    issue.setAssignee(newAssignee);
    store.store(file1, Collections.singletonList(issue));

    Collection<LocalIssuePointer> fileIssues = store.getForFile(file1);
    assertThat(fileIssues).hasSize(1);

    LocalIssuePointer issuePointer = fileIssues.iterator().next();
    assertThat(issuePointer.isResolved()).isTrue();
    assertThat(issuePointer.getAssignee()).isEqualTo(newAssignee);
  }

  @Test
  public void testClearFile() {
    store.clearFile(file1);

    assertThat(store.getForFile(file1)).isEmpty();
    assertThat(store.getForFile(file2)).containsExactly(issue2);
  }

  @Test
  public void testClearAll() {
    store.clear();

    assertThat(store.getForFile(file1)).isEmpty();
    assertThat(store.getForFile(file2)).isEmpty();
  }

  @Test
  public void testDispose() {
    store.disposeComponent();

    assertThat(store.getForFile(file1)).isEmpty();
    assertThat(store.getForFile(file2)).isEmpty();
  }

  @Test
  public void testStoreShouldOverride() {
    store.store(file1, Collections.singletonList(issue2));
    store.store(file2, Collections.singletonList(issue1));

    assertThat(store.getForFile(file1)).containsExactly(issue2);
    assertThat(store.getForFile(file2)).containsExactly(issue1);
  }

  @Test
  public void testClean() {
    store.clean(file1);
    //nothing should be removed
    assertThat(store.getForFile(file1)).containsExactly(issue1);
    assertThat(store.getForFile(file2)).containsExactly(issue2);


    PsiFile psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);

    //add a lot of issues
    store.clear();

    List<LocalIssuePointer> issueList = new ArrayList<>(10_001);
    for (int i = 0; i < 10_001; i++) {
      issueList.add(new LocalIssuePointer(SonarLintTestUtils.createIssue(i), psiFile, null));
    }

    store.store(file1, issueList);

    store.clean(file1);
    assertThat(store.getForFile(file1)).isEmpty();
  }

  private LocalIssuePointer createRangeStoredIssue(int id, String rangeContent, int line) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    when(issue.getStartLine()).thenReturn(line);
    RangeMarker range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    return new LocalIssuePointer(issue, null, range);
  }
}
