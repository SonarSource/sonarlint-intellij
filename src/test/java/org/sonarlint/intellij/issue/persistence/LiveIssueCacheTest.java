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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.issue.LiveIssue;

import static java.util.Collections.singletonList;
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

public class LiveIssueCacheTest extends AbstractSonarLintLightTests {

  private static final int MAX_ENTRIES_FOR_TEST = 10;
  private IssuePersistence store = mock(IssuePersistence.class);

  private LiveIssueCache cache;

  @Before
  public void before() {
    replaceProjectService(IssuePersistence.class, store);
    cache = new LiveIssueCache(getProject(), MAX_ENTRIES_FOR_TEST);
  }

  @Test
  public void should_save_and_read_cache_only() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    LiveIssue issue = createTestIssue("r1");
    cache.insertIssue(file, issue);

    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).containsOnly(issue);

    assertThat(cache.contains(myFixture.copyFileToProject("foo.php", "foo2.php"))).isFalse();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_return_contains_even_if_empty() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    LiveIssue issue = createTestIssue("r1");

    cache.insertIssue(file, issue);
    cache.removeIssues(file, singletonList(issue));

    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).isEmpty();
  }

  @Test
  public void should_not_fallback_persistence() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    LiveIssue issue1 = createTestIssue("r1");

    cache.insertIssue(file, issue1);

    VirtualFile cacheMiss = myFixture.copyFileToProject("foo.php", "foo2.php");
    assertThat(cache.getLive(cacheMiss)).isNull();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_flush_if_full() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.insertIssue(file0, issue1);
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.insertIssue(file1, issue1);

    for (int i = 2; i < MAX_ENTRIES_FOR_TEST; i++) {
      VirtualFile file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.insertIssue(file, issue1);
    }

    // oldest access should be foo1.php after this
    assertThat(cache.getLive(file0)).containsOnly(issue1);

    verifyZeroInteractions(store);

    VirtualFile file = myFixture.copyFileToProject("foo.php", "anotherfile.php");
    cache.insertIssue(file, issue1);

    verify(store).save(eq("foo1.php"), anyCollection());
    assertThat(cache.getLive(file0)).containsOnly(issue1);
    // File1 has been flushed
    assertThat(cache.getLive(file1)).isNull();
  }

  @Test
  public void should_clear_store() {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.insertIssue(file0, issue1);

    cache.clear();
    verify(store).clear();
    assertThat(cache.getLive(file0)).isNull();
  }

  @Test
  public void should_clear_specific_files() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.insertIssue(file0, issue1);

    LiveIssue issue2 = createTestIssue("r2");
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.insertIssue(file1, issue2);

    cache.clear(file1);
    verify(store).clear("foo1.php");
    assertThat(cache.getLive(file1)).isNull();
    assertThat(cache.getLive(file0)).isNotNull();
  }

  @Test
  public void should_flush_all() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.insertIssue(file0, issue1);
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.insertIssue(file1, issue1);

    cache.flushAll();

    verify(store).save(eq("foo0.php"), anyCollection());
    verify(store).save(eq("foo1.php"), anyCollection());
    verifyNoMoreInteractions(store);
  }

  @Test
  public void handle_error_flush() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.insertIssue(file0, issue1);

    assertThrows(IllegalStateException.class, () -> cache.flushAll());
  }

  @Test
  public void error_remove_eldest() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    for (int i = 0; i < MAX_ENTRIES_FOR_TEST; i++) {
      VirtualFile file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.insertIssue(file, issue1);
    }

    VirtualFile extraFile = myFixture.copyFileToProject("foo.php", "another.php");
    assertThrows(IllegalStateException.class, () -> cache.insertIssue(extraFile, issue1));
  }

  @Test
  public void testConcurrentAccess() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    VirtualFile file2 = myFixture.copyFileToProject("foo.php", "foo2.php");

    Runnable r = () -> {
      cache.clear(file1);
      LiveIssue issue1 = createTestIssue("r1");
      cache.insertIssue(file1, issue1);
      LiveIssue issue2 = createTestIssue("r2");
      cache.insertIssue(file1, issue2);
      cache.clear(file2);
      LiveIssue issue3 = createTestIssue("r3");
      cache.insertIssue(file2, issue3);
      LiveIssue issue4 = createTestIssue("r4");
      cache.insertIssue(file2, issue4);
      Collection<LiveIssue> live = cache.getLive(file1);
      if (live != null) {
        assertThat(live).extracting(LiveIssue::getRuleKey).isSubsetOf("r1", "r2");
      }
    };
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Future<?>> tasks = new ArrayList<>();
    IntStream.range(1, 100).forEach(i ->  tasks.add(executor.submit(r)));
    for (Future<?> task : tasks) {
      task.get(1, TimeUnit.MINUTES);
    }

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

}
