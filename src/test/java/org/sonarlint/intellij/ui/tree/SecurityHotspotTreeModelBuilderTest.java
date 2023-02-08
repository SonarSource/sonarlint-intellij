/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.psi.PsiFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.junit.Test;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityHotspotTreeModelBuilderTest {
  private SecurityHotspotTreeModelBuilder treeBuilder = new SecurityHotspotTreeModelBuilder();
  private DefaultTreeModel model  = treeBuilder.createModel();

  @Test
  public void createModel() {
    var model = treeBuilder.createModel();
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  public void testNavigation() {
    Map<VirtualFile, Collection<LiveSecurityHotspot>> data = new HashMap<>();

    // ordering of files: name
    // ordering of security hotspots: creation date (inverse), getVulnerabilityProbability, setRuleName, startLine
    addFile(data, "file1", 2);
    addFile(data, "file2", 2);
    addFile(data, "file3", 2);

    treeBuilder.updateModel(data, "empty");
    var first = treeBuilder.getNextHotspot((AbstractNode) model.getRoot());
    assertThat(first).isNotNull();

    var second = treeBuilder.getNextHotspot(first);
    assertThat(second).isNotNull();

    var third = treeBuilder.getNextHotspot(second);
    assertThat(second).isNotNull();

    assertThat(treeBuilder.getPreviousHotspot(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousHotspot(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousHotspot(first)).isNull();
  }

  @Test
  public void testHotspotComparator() {
    List<LiveSecurityHotspot> list = new ArrayList<>();

    list.add(mockSecurityHotspotPointer("f1", 100, "rule1", VulnerabilityProbability.HIGH, null));
    list.add(mockSecurityHotspotPointer("f1", 100, "rule2", VulnerabilityProbability.HIGH, 1000L));
    list.add(mockSecurityHotspotPointer("f1", 100, "rule3", VulnerabilityProbability.LOW, 2000L));
    list.add(mockSecurityHotspotPointer("f1", 50, "rule4", VulnerabilityProbability.LOW, null));
    list.add(mockSecurityHotspotPointer("f1", 100, "rule5", VulnerabilityProbability.HIGH, null));

    List<LiveSecurityHotspot> sorted = new ArrayList<>(list);
    sorted.sort(new SecurityHotspotTreeModelBuilder.SecurityHotspotComparator());

    // criteria: creation date (most recent, nulls last), getSeverity (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(2), list.get(1), list.get(0), list.get(4), list.get(3));
  }

  private void addFile(Map<VirtualFile, Collection<LiveSecurityHotspot>> data, String fileName, int numSecurityHotspots) {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.isValid()).thenReturn(true);

    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    List<LiveSecurityHotspot> securityHotspotList = new LinkedList<>();

    for (var i = 0; i < numSecurityHotspots; i++) {
      securityHotspotList.add(mockSecurityHotspotPointer(fileName, i, "rule" + i, VulnerabilityProbability.HIGH, (long) i));
    }

    data.put(file, securityHotspotList);
  }

  private static LiveSecurityHotspot mockSecurityHotspotPointer(String path, int startOffset, String rule,
    VulnerabilityProbability vulnerability, @Nullable Long creationDate) {
    var issue = mock(Issue.class);
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    var f = mockFile(path);
    when(issue.getInputFile()).thenReturn(f);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getVulnerabilityProbability()).thenReturn(Optional.of(vulnerability));
    when(issue.getType()).thenReturn(RuleType.SECURITY_HOTSPOT);
    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);
    var ip = new LiveSecurityHotspot(issue, psiFile, Collections.emptyList());
    ip.setCreationDate(creationDate);
    return ip;
  }

  private static ClientInputFile mockFile(String path) {
    var file = mock(ClientInputFile.class);
    when(file.getPath()).thenReturn(path);
    when(file.getCharset()).thenReturn(Charset.defaultCharset());
    when(file.isTest()).thenReturn(false);
    return file;
  }

}
