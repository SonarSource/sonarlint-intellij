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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueNodeTests {
  private IssueNode node;

  @Test
  void testCount() {
    var i = createIssue(System.currentTimeMillis(), "rule");
    node = new IssueNode(i);
    assertThat(node.getFindingCount()).isEqualTo(1);
    assertThat(node.issue()).isEqualTo(i);
  }

  private static LiveIssue createIssue(long date, String message) {
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);
    var issue = mock(RawIssueDto.class);
    when(issue.getPrimaryMessage()).thenReturn(message);
    when(issue.getSeverity()).thenReturn(IssueSeverity.MAJOR);
    when(issue.getType()).thenReturn(RuleType.BUG);
    when(issue.getCleanCodeAttribute()).thenReturn(CleanCodeAttribute.COMPLETE);
    var issuePointer = new LiveIssue(null, issue, file, Collections.emptyList());
    issuePointer.setIntroductionDate(date);
    return issuePointer;
  }
}
