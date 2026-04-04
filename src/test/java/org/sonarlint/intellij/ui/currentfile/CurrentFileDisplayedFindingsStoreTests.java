/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.ui.currentfile;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.filter.FilteredFindings;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentFileDisplayedFindingsStoreTests {

  @Test
  void should_return_empty_findings_before_any_snapshot() {
    var store = newStore();
    var file = mock(VirtualFile.class);

    var forFile = store.getFindingsForFile(file);

    assertThat(forFile.getIssues()).isEmpty();
    assertThat(forFile.getHotspots()).isEmpty();
    assertThat(forFile.getTaints()).isEmpty();
    assertThat(forFile.getDependencyRisks()).isEmpty();
  }

  @Test
  void should_filter_snapshot_by_file_like_findings_tab() {
    var store = newStore();
    var fileA = mock(VirtualFile.class);
    var fileB = mock(VirtualFile.class);
    var issueOnA = liveIssueOnFile(fileA, "on A");
    var issueOnB = liveIssueOnFile(fileB, "on B");
    store.setSnapshot(new FilteredFindings(List.of(issueOnA, issueOnB), List.of(), List.of(), List.of()));

    assertThat(store.getFindingsForFile(fileA).getIssues()).containsExactly(issueOnA);
    assertThat(store.getFindingsForFile(fileB).getIssues()).containsExactly(issueOnB);
  }

  @Test
  void should_replace_snapshot_on_update() {
    var store = newStore();
    var file = mock(VirtualFile.class);
    var first = liveIssueOnFile(file, "first");
    var second = liveIssueOnFile(file, "second");
    store.setSnapshot(new FilteredFindings(List.of(first), List.of(), List.of(), List.of()));
    store.setSnapshot(new FilteredFindings(List.of(second), List.of(), List.of(), List.of()));

    assertThat(store.getFindingsForFile(file).getIssues()).containsExactly(second);
  }

  private static CurrentFileDisplayedFindingsStore newStore() {
    return new CurrentFileDisplayedFindingsStore(mock(Project.class));
  }

  private static LiveIssue liveIssueOnFile(VirtualFile file, String message) {
    var issue = mock(RaisedIssueDto.class);
    when(issue.getPrimaryMessage()).thenReturn(message);
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)));
    return new LiveIssue(null, issue, file, Collections.emptyList());
  }

}
