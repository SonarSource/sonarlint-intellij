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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class LiveIssueCacheTest {

  private IssuePersistence store = mock(IssuePersistence.class);
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);
  private Project project = mock(Project.class);
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private LiveIssueCache cache = new LiveIssueCache(project, mock(ProjectManager.class), store, appUtils, 10);

  @Before
  public void setUp() {
    when(project.getBasePath()).thenReturn("/root");
  }

  @Test
  public void should_save_and_read_cache_only() {
    VirtualFile file = createTestFile("file1");
    LiveIssue issue1 = createTestIssue("will be overwritten");
    LiveIssue issue2 = createTestIssue("r1");
    cache.save(file, Collections.singleton(issue1));
    cache.save(file, Collections.singleton(issue2));

    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).containsOnly(issue2);

    assertThat(cache.contains(createTestFile("file2"))).isFalse();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_return_contains_even_if_empty() {
    VirtualFile file = createTestFile("file1");
    cache.save(file, Collections.emptyList());
    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).isEmpty();
  }

  @Test
  public void should_not_fallback_persistence() {
    VirtualFile file = createTestFile("file1");
    LiveIssue issue1 = createTestIssue("r1");
    cache.save(file, Collections.singleton(issue1));

    VirtualFile cacheMiss = createTestFile("file2");
    assertThat(cache.getLive(cacheMiss)).isNull();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_flush_if_full() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));

    for (int i = 1; i < 10; i++) {
      VirtualFile file = createTestFile("file" + i);
      cache.save(file, Collections.singleton(issue1));
    }

    // oldest access should be file1 after this
    assertThat(cache.getLive(file0)).containsOnly(issue1);

    verifyZeroInteractions(store);

    VirtualFile file = createTestFile("anotherfile");
    cache.save(file, Collections.singleton(issue1));

    verify(store).save(eq("file1"), anyCollection());
  }

  @Test
  public void should_clear_store() {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));

    cache.clear();
    verify(store).clear();
    assertThat(cache.getLive(file0)).isNull();
  }

  @Test
  public void should_clear_specific_files() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));

    LiveIssue issue2 = createTestIssue("r2");
    VirtualFile file1 = createTestFile("file1");
    cache.save(file1, Collections.singleton(issue2));

    cache.clear(file1);
    verify(store).clear("file1");
    assertThat(cache.getLive(file1)).isNull();
    assertThat(cache.getLive(file0)).isNotNull();
  }

  @Test
  public void should_flush_when_requested() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));
    VirtualFile file1 = createTestFile("file1");
    cache.save(file1, Collections.singleton(issue1));

    cache.flushAll();

    verify(store).save(eq("file0"), anyCollection());
    verify(store).save(eq("file1"), anyCollection());
    verifyNoMoreInteractions(store);
  }

  @Test
  public void error_flush() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));

    exception.expect(IllegalStateException.class);
    cache.flushAll();
  }

  @Test
  public void error_remove_eldest() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    for (int i = 0; i < 10; i++) {
      VirtualFile file = createTestFile("file" + i);
      cache.save(file, Collections.singleton(issue1));
    }

    exception.expect(IllegalStateException.class);
    cache.save(createTestFile("anotherfile"), Collections.singleton(issue1));
  }

  @Test
  public void should_flush_on_project_closing() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = createTestFile("file0");
    cache.save(file0, Collections.singleton(issue1));
    VirtualFile file1 = createTestFile("file1");
    cache.save(file1, Collections.singleton(issue1));

    cache.projectClosing(project);

    verify(store).save(eq("file0"), anyCollection());
    verify(store).save(eq("file1"), anyCollection());
    verifyNoMoreInteractions(store);
  }

  private LiveIssue createTestIssue(String ruleKey) {
    LiveIssue issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn(ruleKey);
    when(issue.getAssignee()).thenReturn("assignee");
    when(issue.getRuleName()).thenReturn(ruleKey);
    when(issue.getSeverity()).thenReturn("MAJOR");
    when(issue.getMessage()).thenReturn("msg");

    return issue;
  }

  private VirtualFile createTestFile(String path) {
    VirtualFile file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);
    when(appUtils.getRelativePathForAnalysis(project, file)).thenReturn(path);
    return file;
  }
}
