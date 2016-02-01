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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarsource.sonarlint.core.IssueListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class IssueStoreTest extends SonarTest {
  private IssueStore store;

  private VirtualFile file1;
  private VirtualFile file2;

  private IssuePointer issue1;
  private IssuePointer issue2;

  @Before
  public void setUp() {
    super.setUp();
    file1 = mock(VirtualFile.class);
    file2 = mock(VirtualFile.class);
    store = new IssueStore(project);

    issue1 = createRangeStoredIssue(1);
    issue2 = createRangeStoredIssue(2);

    store.store(file1, Collections.singletonList(issue1));
    store.store(file2, Collections.singletonList(issue2));
  }

  @Test
  public void testStore() {
    assertThat(store.getForFile(file1)).containsExactly(issue1);
    assertThat(store.getForFile(file2)).containsExactly(issue2);
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

    //add a lot of issues
    store.clear();

    List<IssuePointer> issueList = new ArrayList<>(100_000);
    for (int i = 0; i < 100_000; i++) {
      issueList.add(createRangeStoredIssue(i));
    }

    store.store(file1, issueList);

    store.clean(file1);
    assertThat(store.getForFile(file1)).isEmpty();
  }

  private static IssuePointer createRangeStoredIssue(int id) {
    IssueListener.Issue issue = SonarLintTestUtils.createIssue(id);
    RangeMarker range = mock(RangeMarker.class);
    return new IssuePointer(issue, null, range);
  }
}
