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
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHotspotNodeTests {

  @Test
  void testCount() {
    var i = createSecurityHotspot();
    var node = new LiveSecurityHotspotNode(i, false);
    assertThat(node.getFindingCount()).isEqualTo(1);
    assertThat(node.getHotspot()).isEqualTo(i);
  }

  private static LiveSecurityHotspot createSecurityHotspot() {
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);
    var issue = mock(RaisedHotspotDto.class);
    when(issue.getPrimaryMessage()).thenReturn("rule");
    when(issue.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.HIGH);
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
    when(issue.getType()).thenReturn(RuleType.BUG);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getCleanCodeAttribute()).thenReturn(CleanCodeAttribute.COMPLETE);
    return new LiveSecurityHotspot(null, issue, file, Collections.emptyList());
  }
}
