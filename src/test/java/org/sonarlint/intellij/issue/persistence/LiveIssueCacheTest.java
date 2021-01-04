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
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.issue.LiveIssue;

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
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private LiveIssueCache cache;

  @Before
  public void before() {
    replaceProjectService(IssuePersistence.class, store);
    cache = new LiveIssueCache(getProject(), MAX_ENTRIES_FOR_TEST);
  }

  @Test
  public void should_save_and_read_cache_only() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    LiveIssue issue1 = createTestIssue("will be overwritten");
    LiveIssue issue2 = createTestIssue("r1");
    cache.save(file, Collections.singleton(issue1));
    cache.save(file, Collections.singleton(issue2));

    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).containsOnly(issue2);

    assertThat(cache.contains(myFixture.copyFileToProject("foo.php", "foo2.php"))).isFalse();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_return_contains_even_if_empty() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    cache.save(file, Collections.emptyList());
    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).isEmpty();
  }

  @Test
  public void should_not_fallback_persistence() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    LiveIssue issue1 = createTestIssue("r1");
    cache.save(file, Collections.singleton(issue1));

    VirtualFile cacheMiss = myFixture.copyFileToProject("foo.php", "foo2.php");
    assertThat(cache.getLive(cacheMiss)).isNull();

    verifyZeroInteractions(store);
  }

  @Test
  public void should_flush_if_full() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.save(file0, Collections.singleton(issue1));

    for (int i = 1; i < MAX_ENTRIES_FOR_TEST; i++) {
      VirtualFile file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.save(file, Collections.singleton(issue1));
    }

    // oldest access should be foo1.php after this
    assertThat(cache.getLive(file0)).containsOnly(issue1);

    verifyZeroInteractions(store);

    VirtualFile file = myFixture.copyFileToProject("foo.php", "anotherfile.php");
    cache.save(file, Collections.singleton(issue1));

    verify(store).save(eq("foo1.php"), anyCollection());
  }

  @Test
  public void should_clear_store() {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.save(file0, Collections.singleton(issue1));

    cache.clear();
    verify(store).clear();
    assertThat(cache.getLive(file0)).isNull();
  }

  @Test
  public void should_clear_specific_files() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.save(file0, Collections.singleton(issue1));

    LiveIssue issue2 = createTestIssue("r2");
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.save(file1, Collections.singleton(issue2));

    cache.clear(file1);
    verify(store).clear("foo1.php");
    assertThat(cache.getLive(file1)).isNull();
    assertThat(cache.getLive(file0)).isNotNull();
  }

  @Test
  public void should_flush_when_requested() throws IOException {
    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.save(file0, Collections.singleton(issue1));
    VirtualFile file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.save(file1, Collections.singleton(issue1));

    cache.flushAll();

    verify(store).save(eq("foo0.php"), anyCollection());
    verify(store).save(eq("foo1.php"), anyCollection());
    verifyNoMoreInteractions(store);
  }

  @Test
  public void error_flush() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    VirtualFile file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.save(file0, Collections.singleton(issue1));

    exception.expect(IllegalStateException.class);
    cache.flushAll();
  }

  @Test
  public void error_remove_eldest() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    LiveIssue issue1 = createTestIssue("r1");
    for (int i = 0; i < MAX_ENTRIES_FOR_TEST; i++) {
      VirtualFile file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.save(file, Collections.singleton(issue1));
    }

    exception.expect(IllegalStateException.class);
    cache.save(myFixture.copyFileToProject("foo.php", "another.php"), Collections.singleton(issue1));
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
