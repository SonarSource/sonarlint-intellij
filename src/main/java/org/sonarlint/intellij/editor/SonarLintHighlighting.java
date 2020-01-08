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
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  /**
   * Create highlighting with {@link UpdateHighlightersUtil}. It will manage internally the {@link RangeHighlighter}, and get
   * it similarly to the way {@link com.intellij.codeHighlighting.Pass} do it.
   * Tooltip will be displayed on mouse hover by {@link com.intellij.codeInsight.daemon.impl.DaemonListeners}.
   * Creating the {@link HighlightInfo} with high severity will ensure that it will override most other highlighters.
   * The alternative would be to get and manage directly {@link RangeHighlighter} with a {@link MarkupModel} from the
   * document (or editors). This would allow to use a custom renderer, but we would have to manage tooltips by ourselves, separately.
   *
   * @see com.intellij.codeInsight.hint.HintManager
   * @see com.intellij.codeInsight.hint.ShowParameterInfoHandler
   * @see HintHint
   * @see CustomHighlighterRenderer
   * @see com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
   * @see com.intellij.codeInsight.highlighting.BraceHighlightingHandler
   */
  public void highlightFlowsWithHighlightersUtil(RangeMarker rangeMarker, @Nullable String message, List<LiveIssue.Flow> flows) {
    stopBlinking();
    HighlightInfo primaryInfo = createHighlight(rangeMarker, message);

    List<HighlightInfo> infos = flows.stream()
      .flatMap(f -> f.locations().stream()
        .filter(Objects::nonNull)
        .map(l -> createHighlight(l.location(), l.message())))
      .collect(Collectors.toList());

    infos.add(primaryInfo);

    UpdateHighlightersUtil.setHighlightersToEditor(project, rangeMarker.getDocument(), 0,
      rangeMarker.getDocument().getTextLength(), infos, null, HIGHLIGHT_GROUP_ID);
    currentHighlightedDoc = rangeMarker.getDocument();

    Editor[] editors = EditorFactory.getInstance().getEditors(rangeMarker.getDocument(), project);
    List<Segment> segments = Stream.concat(flows.stream()
        .flatMap(f -> f.locations().stream()
          .map(LiveIssue.IssueLocation::location)),
      Stream.of(rangeMarker)).collect(Collectors.toList());

    Arrays.stream(editors).forEach(editor -> {
      blinker = new RangeBlinker(editor, new TextAttributes(null, null, JBColor.YELLOW, EffectType.BOXED, Font.PLAIN), 3);
      blinker.resetMarkers(segments);
      blinker.startBlinking();
    });
  }

  public boolean isActiveInEditor(Editor editor) {
    return currentHighlightedDoc != null && currentHighlightedDoc.equals(editor.getDocument());
  }

  private static HighlightInfo createHighlight(RangeMarker location, @Nullable String message) {
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
