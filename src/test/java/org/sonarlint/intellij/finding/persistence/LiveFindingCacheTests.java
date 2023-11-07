/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.finding.persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LiveFindingCacheTests extends AbstractSonarLintLightTests {

  private static final int MAX_ENTRIES_FOR_TEST = 10;
  private FindingPersistence<LiveIssue> store = mock(FindingPersistence.class);

  private LiveFindingCache<LiveIssue> cache;

  @BeforeEach
  void before() {
    cache = new LiveFindingCache<>(getProject(), store, MAX_ENTRIES_FOR_TEST);
  }

  @Test
  void should_save_and_read_cache_only() throws IOException {
    var file = myFixture.copyFileToProject("foo.php", "foo.php");
    var issue = createTestIssue("r1");
    cache.replaceFindings(Map.of(file, List.of(issue)));

    assertThat(cache.contains(file)).isTrue();
    assertThat(cache.getLive(file)).containsOnly(issue);

    assertThat(cache.contains(myFixture.copyFileToProject("foo.php", "foo2.php"))).isFalse();

    verify(store).save("foo.php", List.of(issue));
  }

  @Test
  void should_flush_if_full() {
    var issue1 = createTestIssue("r1");
    var file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.replaceFindings(Map.of(file0, List.of(issue1)));
    var file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.replaceFindings(Map.of(file1, List.of(issue1)));

    for (var i = 2; i < MAX_ENTRIES_FOR_TEST; i++) {
      var file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.replaceFindings(Map.of(file, List.of(issue1)));
    }

    // oldest access should be foo1.php after this
    assertThat(cache.getLive(file0)).containsOnly(issue1);

    var file = myFixture.copyFileToProject("foo.php", "anotherfile.php");
    cache.replaceFindings(Map.of(file, List.of(issue1)));

    assertThat(cache.getLive(file0)).containsOnly(issue1);
    // File1 has been flushed
    assertThat(cache.getLive(file1)).isNull();
  }

  @Test
  void should_clear_store() {
    var issue1 = createTestIssue("r1");
    var file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.replaceFindings(Map.of(file0, List.of(issue1)));

    cache.clear();
    verify(store).clear();
    assertThat(cache.getLive(file0)).isNull();
  }

  @Test
  void replace_findings_should_flush_all() throws IOException {
    var issue1 = createTestIssue("r1");
    var file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    var file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    cache.replaceFindings(Map.of(file0, List.of(issue1), file1, List.of(issue1)));

    verify(store).save(eq("foo0.php"), anyCollection());
    verify(store).save(eq("foo1.php"), anyCollection());
    verifyNoMoreInteractions(store);
  }

  @Test
  void handle_error_flush() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    var issue1 = createTestIssue("r1");
    var file0 = myFixture.copyFileToProject("foo.php", "foo0.php");
    cache.replaceFindings(Map.of(file0, List.of(issue1)));

    assertThat(((SonarLintConsoleTestImpl)getConsole()).getLastMessage()).contains("Cannot flush issues");
  }

  @Test
  void error_remove_eldest() throws IOException {
    doThrow(new IOException()).when(store).save(anyString(), anyCollection());

    var issue1 = createTestIssue("r1");
    for (var i = 0; i < MAX_ENTRIES_FOR_TEST; i++) {
      var file = myFixture.copyFileToProject("foo.php", "foo" + i + ".php");
      cache.replaceFindings(Map.of(file, List.of(issue1)));
    }

    var extraFile = myFixture.copyFileToProject("foo.php", "another.php");
    assertThrows(IllegalStateException.class, () -> cache.replaceFindings(Map.of(extraFile, List.of(issue1))));
  }

  @Test
  void testConcurrentAccess() throws Exception {
    var file1 = myFixture.copyFileToProject("foo.php", "foo1.php");
    var file2 = myFixture.copyFileToProject("foo.php", "foo2.php");

    Runnable r = () -> {
      LiveIssue issue1 = createTestIssue("r1");
      cache.replaceFindings(Map.of(file1, List.of(issue1)));
      LiveIssue issue2 = createTestIssue("r2");
      cache.replaceFindings(Map.of(file1, List.of(issue2)));
      LiveIssue issue3 = createTestIssue("r3");
      cache.replaceFindings(Map.of(file2, List.of(issue3)));
      LiveIssue issue4 = createTestIssue("r4");
      cache.replaceFindings(Map.of(file2, List.of(issue4)));
      Collection<LiveIssue> live = cache.getLive(file1);
      if (live != null) {
        assertThat(live).extracting(LiveIssue::getRuleKey).isSubsetOf("r1", "r2");
      }
    };
    var executor = Executors.newFixedThreadPool(10);
    List<Future<?>> tasks = new ArrayList<>();
    IntStream.range(1, 100).forEach(i -> tasks.add(executor.submit(r)));
    for (Future<?> task : tasks) {
      task.get(1, TimeUnit.MINUTES);
    }
  }

  private LiveIssue createTestIssue(String ruleKey) {
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn(ruleKey);
    when(issue.getUserSeverity()).thenReturn(IssueSeverity.MAJOR);
    when(issue.getMessage()).thenReturn("msg");

    return issue;
  }

}
