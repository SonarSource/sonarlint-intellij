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
package org.sonarlint.intellij.finding.tracking;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.persistence.CachedFindings;
import org.sonarlint.intellij.finding.persistence.LiveFindingCache;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalHistoryFindingTrackerTests extends AbstractSonarLintLightTests {
  private LocalHistoryFindingTracker underTest;
  private LiveFindingCache<LiveIssue> cache = mock(LiveFindingCache.class);
  private VirtualFile file1 = mock(VirtualFile.class);
  private Document document = mock(Document.class);
  private LiveIssue issue1;

  @BeforeEach
  void prepare() {
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file1.getPath()).thenReturn("file1");

    issue1 = createRangeStoredIssue(1, "issue 1", 10);

    when(cache.contains(file1)).thenReturn(true);
    when(cache.getLive(file1)).thenReturn(List.of(issue1));
  }

  @Test
  void testTracking() {
    // tracking based on setRuleKey / line number
    var previousIssue = createRangeStoredIssue(1, "issue 1", 10);
    previousIssue.setIntroductionDate(1000L);
    previousIssue.setSeverity(IssueSeverity.INFO);
    previousIssue.setType(RuleType.BUG);

    var rawIssue = createRangeStoredIssue(1, "issue 1", 10);
    rawIssue.setIntroductionDate(2000L);
    rawIssue.setSeverity(IssueSeverity.MAJOR);
    rawIssue.setType(RuleType.CODE_SMELL);
    underTest = new LocalHistoryFindingTracker(new CachedFindings(Map.of(file1, List.of(previousIssue)), emptyMap(), Set.of(file1)));

    underTest.matchWithPreviousIssue(file1, rawIssue);

    assertThat(rawIssue.getIntroductionDate()).isEqualTo(1000);
    assertThat(rawIssue.getUserSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(rawIssue.getType()).isEqualTo(RuleType.CODE_SMELL);
  }

  @Test
  void testTracking_by_checksum() {
    // tracking based on checksum
    issue1.setIntroductionDate(1000L);

    // line is different
    var i2 = createRangeStoredIssue(1, "issue 1", 11);
    i2.setIntroductionDate(2000L);
    underTest = new LocalHistoryFindingTracker(new CachedFindings(Map.of(file1, List.of(issue1)), emptyMap(), Set.of(file1)));

    underTest.matchWithPreviousIssue(file1, i2);

    assertThat(i2.getIntroductionDate()).isEqualTo(1000);
  }

  @Test
  void testTracking_removes_matched_issues_for_future_tracking() {
    var previousIssue = createRangeStoredIssue(1, "issue 1", 10);
    previousIssue.setIntroductionDate(1000L);
    previousIssue.setSeverity(IssueSeverity.INFO);
    previousIssue.setType(RuleType.BUG);

    var rawIssue = createRangeStoredIssue(1, "issue 1", 10);
    rawIssue.setIntroductionDate(2000L);
    rawIssue.setSeverity(IssueSeverity.MAJOR);
    rawIssue.setType(RuleType.CODE_SMELL);
    var rawIssue2 = createRangeStoredIssue(1, "issue 1", 10);
    rawIssue2.setIntroductionDate(3000L);
    rawIssue2.setSeverity(IssueSeverity.MAJOR);
    rawIssue2.setType(RuleType.CODE_SMELL);
    underTest = new LocalHistoryFindingTracker(new CachedFindings(Map.of(file1, List.of(previousIssue)), emptyMap(), emptySet()));

    underTest.matchWithPreviousIssue(file1, rawIssue);
    underTest.matchWithPreviousIssue(file1, rawIssue2);

    assertThat(rawIssue2.getIntroductionDate()).isEqualTo(3000);
  }

  private LiveIssue createRangeStoredIssue(int id, String rangeContent, int line) {
    var issue = SonarLintTestUtils.createIssue(id);
    when(issue.getTextRange()).thenReturn(new TextRangeDto(line, 1, line, 2));
    var range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);
    return new LiveIssue(getModule(), issue, file, range, null, Collections.emptyList());
  }
}
