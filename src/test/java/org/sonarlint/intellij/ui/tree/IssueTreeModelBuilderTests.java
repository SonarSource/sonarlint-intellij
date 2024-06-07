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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.HIGH;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.LOW;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MINOR;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality.MAINTAINABILITY;

class IssueTreeModelBuilderTests extends AbstractSonarLintLightTests {
  private IssueTreeModelBuilder treeBuilder;
  private DefaultTreeModel model;

  @BeforeEach
  void prepare() {
    treeBuilder = new IssueTreeModelBuilder(getProject());
    model = treeBuilder.createModel(false);
  }

  @Test
  void createModel() {
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  void testNavigation() {
    var data = new HashMap<VirtualFile, Collection<LiveIssue>>();

    // ordering of files: name
    // ordering of issues: creation date (inverse), getSeverity, setRuleName, startLine
    addFile(data, "file1", 2);
    addFile(data, "file2", 2);
    addFile(data, "file3", 2);

    treeBuilder.updateModel(data);
    var first = treeBuilder.getNextIssue((AbstractNode) model.getRoot());
    assertThat(first).isNotNull();

    var second = treeBuilder.getNextIssue(first);
    assertThat(second).isNotNull();

    var third = treeBuilder.getNextIssue(second);
    assertThat(third).isNotNull();

    assertThat(treeBuilder.getPreviousIssue(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousIssue(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousIssue(first)).isNull();
  }

  @Test
  void testIssueComparator() {
    var list = new ArrayList<LiveIssue>();

    list.add(mockIssuePointer(100, "rule1", MAJOR, null));
    list.add(mockIssuePointer(100, "rule2", MAJOR, Instant.now().minusSeconds(50)));
    list.add(mockIssuePointer(100, "rule3", MINOR, Instant.now().minusSeconds(20)));
    list.add(mockIssuePointer(50, "rule4", MINOR, null));
    list.add(mockIssuePointer(100, "rule5", MAJOR, null));

    List<LiveIssue> sorted = new ArrayList<>(list);
    sorted.sort(new IssueTreeModelBuilder.IssueComparator());

    // criteria: creation date (most recent, nulls last), getSeverity (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(2), list.get(1), list.get(0), list.get(4), list.get(3));
  }

  @Test
  void testIssueComparatorNewCct() {
    var list = new ArrayList<LiveIssue>();

    list.add(mockIssuePointer(100, "rule1", List.of(new ImpactDto(MAINTAINABILITY, HIGH)), null));
    list.add(mockIssuePointer(100, "rule2", List.of(new ImpactDto(MAINTAINABILITY, HIGH)), Instant.now().minusSeconds(50)));
    list.add(mockIssuePointer(100, "rule3", List.of(new ImpactDto(MAINTAINABILITY, LOW)), Instant.now().minusSeconds(20)));
    list.add(mockIssuePointer(50, "rule4", List.of(new ImpactDto(MAINTAINABILITY, LOW)), null));
    list.add(mockIssuePointer(100, "rule5", List.of(new ImpactDto(MAINTAINABILITY, HIGH)), null));

    var sorted = new ArrayList<>(list);
    sorted.sort(new IssueTreeModelBuilder.IssueComparator());

    // criteria: creation date (most recent, nulls last), getImpact (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(2), list.get(1), list.get(0), list.get(4), list.get(3));
  }

  private void addFile(Map<VirtualFile, Collection<LiveIssue>> data, String fileName, int numIssues) {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.isValid()).thenReturn(true);

    var issueList = new LinkedList<LiveIssue>();

    for (var i = 0; i < numIssues; i++) {
      issueList.add(mockIssuePointer(i, "rule" + i, MAJOR, Instant.now()));
    }

    data.put(file, issueList);
  }

  private LiveIssue mockIssuePointer(int startOffset, String rule, IssueSeverity severity, @Nullable Instant introductionDate) {
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);

    var issue = mock(RaisedIssueDto.class);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getSeverity()).thenReturn(severity);
    when(issue.getType()).thenReturn(RuleType.BUG);
    when(issue.getCleanCodeAttribute()).thenReturn(CleanCodeAttribute.COMPLETE);

    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);

    var liveIssue = new LiveIssue(getModule(), issue, file, Collections.emptyList());
    liveIssue.setIntroductionDate(introductionDate);
    return liveIssue;
  }

  private LiveIssue mockIssuePointer(int startOffset, String rule, List<ImpactDto> impacts, @Nullable Instant introductionDate) {
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);

    var issue = mock(RaisedIssueDto.class);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getType()).thenReturn(RuleType.BUG);
    when(issue.getCleanCodeAttribute()).thenReturn(CleanCodeAttribute.CONVENTIONAL);
    when(issue.getImpacts()).thenReturn(impacts);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);

    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);

    var liveIssue = new LiveIssue(getModule(), issue, file, Collections.emptyList());
    liveIssue.setIntroductionDate(introductionDate);
    return liveIssue;
  }

}
