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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.filters.FilterSecurityHotspotSettings;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHotspotTreeModelBuilderTests extends AbstractSonarLintLightTests {

  private final CodeAnalyzerRestarter codeAnalyzerRestarter = mock(CodeAnalyzerRestarter.class);
  private SecurityHotspotTreeModelBuilder treeBuilder;
  private DefaultTreeModel model;

  @BeforeEach
  void init() {
    treeBuilder = new SecurityHotspotTreeModelBuilder();
    model = treeBuilder.createModel();
    FilterSecurityHotspotSettings.setCurrentlySelectedFilter(SecurityHotspotFilters.DEFAULT_FILTER);
    FilterSecurityHotspotSettings.setResolved(false);
    replaceProjectService(CodeAnalyzerRestarter.class, codeAnalyzerRestarter);
  }

  @Test
  void createModel() {
    var model = treeBuilder.createModel();
    assertThat(model.getRoot()).isNotNull();
  }

  @Test
  void testNavigation() {
    Map<VirtualFile, Collection<LiveSecurityHotspot>> data = new HashMap<>();

    // ordering of files: name
    // ordering of security hotspots: getVulnerabilityProbability, introduction date, range start, rule name
    addFile(data, "file1", 2);
    addFile(data, "file2", 2);
    addFile(data, "file3", 2);

    treeBuilder.updateModel(data, "empty");
    var first = treeBuilder.getNextHotspot((AbstractNode) model.getRoot());
    assertThat(first).isNotNull();

    var second = treeBuilder.getNextHotspot(first);
    assertThat(second).isNotNull();

    var third = treeBuilder.getNextHotspot(second);
    assertThat(third).isNotNull();

    assertThat(treeBuilder.getPreviousHotspot(third)).isEqualTo(second);
    assertThat(treeBuilder.getPreviousHotspot(second)).isEqualTo(first);
    assertThat(treeBuilder.getPreviousHotspot(first)).isNull();
  }

  @Test
  void testSecurityHotspotComparator() {
    List<LiveSecurityHotspot> list = new ArrayList<>();

    list.add(mockSecurityHotspot("f1", 100, "rule1", VulnerabilityProbability.HIGH, null));
    list.add(mockSecurityHotspot("f1", 75, "rule2", VulnerabilityProbability.HIGH, 1000L));
    list.add(mockSecurityHotspot("f1", 100, "rule3", VulnerabilityProbability.LOW, 2000L));
    list.add(mockSecurityHotspot("f1", 50, "rule4", VulnerabilityProbability.LOW, null));
    list.add(mockSecurityHotspot("f1", 100, "rule5", VulnerabilityProbability.HIGH, null));

    List<LiveSecurityHotspot> sorted = new ArrayList<>(list);
    sorted.sort(new SecurityHotspotTreeModelBuilder.SecurityHotspotComparator());

    // criteria: vulnerability probability, range start, rule name, uid
    assertThat(sorted).containsExactly(list.get(1), list.get(0), list.get(4), list.get(3), list.get(2));
  }

  @Test
  void testSecurityHotspotWithoutFileComparator() {
    List<LiveSecurityHotspotNode> list = new ArrayList<>();

    list.add(mockSecurityHotspotNode("f1", 50, "rule1", VulnerabilityProbability.HIGH));
    list.add(mockSecurityHotspotNode("f2", 100, "rule2", VulnerabilityProbability.HIGH));
    list.add(mockSecurityHotspotNode("f1", 50, "rule1", VulnerabilityProbability.LOW));
    list.add(mockSecurityHotspotNode("f2", 100, "rule1", VulnerabilityProbability.HIGH));
    list.add(mockSecurityHotspotNode("f2", 50, "rule1", VulnerabilityProbability.HIGH));
    List<LiveSecurityHotspotNode> sorted = new ArrayList<>(list);
    sorted.sort(new SecurityHotspotTreeModelBuilder.LiveSecurityHotspotNodeComparator());

    // criteria: creation date (most recent, nulls last), getSeverity (highest first), rule alphabetically
    assertThat(sorted).containsExactly(list.get(0), list.get(4), list.get(3), list.get(1), list.get(2));
  }

  @Test
  void testSecurityHotspotFilteringSonarQube() {
    Map<VirtualFile, Collection<LiveSecurityHotspot>> data = new HashMap<>();

    addFileWithStatusAndFindingKeyForHotspot(data, "file1", 1, HotspotReviewStatus.TO_REVIEW, null);
    addFileWithStatusAndFindingKeyForHotspot(data, "file2", 1, HotspotReviewStatus.TO_REVIEW, "keyA");
    addFileWithStatusAndFindingKeyForHotspot(data, "file3", 1, HotspotReviewStatus.ACKNOWLEDGED, "keyB");
    addFileWithStatusAndFindingKeyForHotspot(data, "file4", 1, HotspotReviewStatus.FIXED, "keyC");
    addFileWithStatusAndFindingKeyForHotspot(data, "file5", 1, HotspotReviewStatus.SAFE, "keyD");

    treeBuilder.updateModelWithoutFileNode(data, "empty");

    assertThat(treeBuilder.filterSecurityHotspots(getProject(), SecurityHotspotFilters.SHOW_ALL)).isEqualTo(3);
    assertThat(treeBuilder.getFilteredNodes()).hasSize(3);

    assertThat(treeBuilder.filterSecurityHotspots(getProject(), SecurityHotspotFilters.EXISTING_ON_SONARQUBE)).isEqualTo(2);
    assertThat(treeBuilder.getFilteredNodes()).hasSize(2);

    assertThat(treeBuilder.filterSecurityHotspots(getProject(), SecurityHotspotFilters.LOCAL_ONLY)).isEqualTo(1);
    assertThat(treeBuilder.getFilteredNodes()).hasSize(1);
  }

  @Test
  void testSecurityHotspotFilteringResolvedSonarQube() {
    Map<VirtualFile, Collection<LiveSecurityHotspot>> data = new HashMap<>();

    addFileWithStatusAndFindingKeyForHotspot(data, "file2", 1, HotspotReviewStatus.TO_REVIEW, "keyA");
    addFileWithStatusAndFindingKeyForHotspot(data, "file2", 1, HotspotReviewStatus.ACKNOWLEDGED, "keyB");
    addFileWithStatusAndFindingKeyForHotspot(data, "file3", 1, HotspotReviewStatus.FIXED, "keyC");
    addFileWithStatusAndFindingKeyForHotspot(data, "file4", 1, HotspotReviewStatus.SAFE, "keyD");

    treeBuilder.updateModelWithoutFileNode(data, "empty");

    FilterSecurityHotspotSettings.setResolved(false);
    assertThat(treeBuilder.filterSecurityHotspots(getProject(), SecurityHotspotFilters.SHOW_ALL)).isEqualTo(2);
    assertThat(treeBuilder.getFilteredNodes()).hasSize(2);

    FilterSecurityHotspotSettings.setResolved(true);
    assertThat(treeBuilder.filterSecurityHotspots(getProject(), SecurityHotspotFilters.SHOW_ALL)).isEqualTo(4);
    assertThat(treeBuilder.getFilteredNodes()).hasSize(4);
  }

  private void addFile(Map<VirtualFile, Collection<LiveSecurityHotspot>> data, String fileName, int numSecurityHotspots) {
    addFileWithStatusAndFindingKeyForHotspot(data, fileName, numSecurityHotspots, HotspotReviewStatus.TO_REVIEW, null);
  }

  private void addFileWithStatusAndFindingKeyForHotspot(Map<VirtualFile, Collection<LiveSecurityHotspot>> data, String fileName, int numSecurityHotspots,
    HotspotReviewStatus status, @Nullable String serverFindingKey) {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(fileName);
    when(file.getPath()).thenReturn("path_" + fileName);
    when(file.isValid()).thenReturn(true);

    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    List<LiveSecurityHotspot> securityHotspotList = new LinkedList<>();

    for (var i = 0; i < numSecurityHotspots; i++) {
      if (serverFindingKey != null) {
        securityHotspotList.add(mockSecurityHotspot(fileName, i, "rule" + i, VulnerabilityProbability.HIGH, (long) i, status, serverFindingKey + "" + (i + 1)));
      } else {
        securityHotspotList.add(mockSecurityHotspot(fileName, i, "rule" + i, VulnerabilityProbability.HIGH, (long) i, status, null));
      }
    }

    data.put(file, securityHotspotList);
  }

  private static LiveSecurityHotspot mockSecurityHotspot(String path, int startOffset, String rule,
    VulnerabilityProbability vulnerability, @Nullable Long introductionDate, HotspotReviewStatus status, @Nullable String serverFindingKey) {

    var virtualFile = mock(VirtualFile.class);
    when(virtualFile.getPath()).thenReturn(path);
    when(virtualFile.isValid()).thenReturn(true);
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);

    var issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn(rule);
    when(issue.getVulnerabilityProbability()).thenReturn(Optional.of(vulnerability));
    when(issue.getType()).thenReturn(RuleType.SECURITY_HOTSPOT);

    var document = mock(Document.class);
    when(document.getText(any())).thenReturn("");
    var marker = mock(RangeMarker.class);
    when(marker.getStartOffset()).thenReturn(startOffset);
    when(marker.getEndOffset()).thenReturn(100);
    when(marker.getDocument()).thenReturn(document);
    when(marker.isValid()).thenReturn(true);

    var securityHotspot = new LiveSecurityHotspot(issue, psiFile, marker, null, Collections.emptyList());
    securityHotspot.setIntroductionDate(introductionDate);
    securityHotspot.setStatus(status);
    securityHotspot.setServerFindingKey(serverFindingKey);

    return securityHotspot;
  }

  private static LiveSecurityHotspot mockSecurityHotspot(String path, int startOffset, String rule,
    VulnerabilityProbability vulnerability, @Nullable Long introductionDate) {
    return mockSecurityHotspot(path, startOffset, rule, vulnerability, introductionDate, HotspotReviewStatus.TO_REVIEW, null);
  }

  private static LiveSecurityHotspotNode mockSecurityHotspotNode(String path, int startOffset, String rule,
    VulnerabilityProbability vulnerability) {
    var securityHotspot = mockSecurityHotspot(path, startOffset, rule, vulnerability, null, HotspotReviewStatus.TO_REVIEW, null);
    return new LiveSecurityHotspotNode(securityHotspot, false);
  }

}
