/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.issue.LiveIssue;

public class SonarLintHighlighting {
  private static final int HIGHLIGHT_GROUP_ID = 1001;
  private final Project project;
  private Document currentHighlightedDoc = null;
  private RangeBlinker blinker = null;

  public SonarLintHighlighting(Project project) {
    this.project = project;
  }

  public void removeHighlightingFlows() {
    if (currentHighlightedDoc != null) {
      UpdateHighlightersUtil.setHighlightersToEditor(project, currentHighlightedDoc, 0,
        currentHighlightedDoc.getTextLength(), Collections.emptyList(), null, HIGHLIGHT_GROUP_ID);
      currentHighlightedDoc = null;
    }
    stopBlinking();
  }

  private void stopBlinking() {
    if (blinker != null) {
      blinker.stopBlinking();
      blinker = null;
    }
  }

  public void highlightFlow(LiveIssue.Flow flow) {
    if (flow.locations().isEmpty()) {
      return;
    }

    updateHighlights(createFlowHighlights(flow), flow.locations().get(0).location().getDocument());
  }

  public void highlightIssue(LiveIssue issue) {
    RangeMarker issueRange = issue.getRange();
    if (issueRange == null) {
      return;
    }
    List<HighlightInfo> highlights = issue.flows().stream().findFirst().map(SonarLintHighlighting::createFlowHighlights).orElse(new ArrayList<>());
    highlights.add(createHighlight(issueRange, issue.getMessage()));

    updateHighlights(highlights, issueRange.getDocument());
  }

  public void highlightLocation(RangeMarker rangeMarker, @Nullable String message) {
    List<HighlightInfo> highlights = Collections.singletonList(createHighlight(rangeMarker, message));
    updateHighlights(highlights, rangeMarker.getDocument());
  }

  private void updateHighlights(List<HighlightInfo> highlights, Document document) {
    stopBlinking();

    highlightInDocument(highlights, document);

    blinkLocations(highlights, document);
  }

  private void highlightInDocument(List<HighlightInfo> highlights, Document document) {
    UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0,
      document.getTextLength(), highlights, null, HIGHLIGHT_GROUP_ID);
    currentHighlightedDoc = document;
  }

  private void blinkLocations(List<HighlightInfo> highlights, Document document) {
    Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
    Arrays.stream(editors).forEach(editor -> {
      blinker = new RangeBlinker(editor, new TextAttributes(null, null, JBColor.YELLOW, EffectType.BOXED, Font.PLAIN), 3);
      blinker.blinkHighlights(highlights);
    });
  }

  public boolean isActiveInEditor(Editor editor) {
    return currentHighlightedDoc != null && currentHighlightedDoc.equals(editor.getDocument());
  }

  private static List<HighlightInfo> createFlowHighlights(LiveIssue.Flow flow) {
    return flow.locations().stream()
        .filter(Objects::nonNull)
        .map(l -> createHighlight(l.location(), l.message()))
        .collect(Collectors.toList());
  }

  private static HighlightInfo createHighlight(RangeMarker location, @Nullable String message) {
    // Creating the HighlightInfo with high severity will ensure that it will override most other highlighters.
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(location.getStartOffset(), location.getEndOffset())
      .severity(HighlightSeverity.ERROR)
      .textAttributes(SonarLintTextAttributes.SELECTED);

    if (message != null && !message.isEmpty() && !"...".equals(message)) {
      builder.descriptionAndTooltip("SonarLint: " + message);
    }
    return builder.create();
  }
}
