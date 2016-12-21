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
import com.intellij.util.ui.RangeBlinker;
import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
   * Create highlighting with {@link UpdateHighlightersUtil}. It will manage internally the {@link RangeHighlighter}, and create
   * it similarly to the way {@link com.intellij.codeHighlighting.Pass} do it.
   * Tooltip will be displayed on mouse hover by {@link com.intellij.codeInsight.daemon.impl.DaemonListeners}.
   * Creating the {@link HighlightInfo} with high severity will ensure that it will override most other highlighters.
   *
   * The alternative would be to create and manage directly {@link RangeHighlighter} with a {@link MarkupModel} from the
   * document (or editors). This would allow to use a custom renderer, but we would have to manage tooltips by ourselves, separately.
   * @see com.intellij.codeInsight.hint.HintManager
   * @see com.intellij.codeInsight.hint.ShowParameterInfoHandler
   * @see HintHint
   * @see CustomHighlighterRenderer
   * @see com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
   * @see com.intellij.codeInsight.highlighting.BraceHighlightingHandler
   */
  public void highlightFlowsWithHighlightersUtil(RangeMarker rangeMarker, String message, List<LiveIssue.Flow> flows) {
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
      RangeBlinker blinked = new RangeBlinker(editor, new TextAttributes(null, null, JBColor.YELLOW, EffectType.BOXED, Font.PLAIN), 3);
      blinked.resetMarkers(segments);
      blinked.startBlinking();
    });
  }

  private static HighlightInfo createHighlight(RangeMarker location, String message) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(location.getStartOffset(), location.getEndOffset())
      .descriptionAndTooltip(message)
      .severity(HighlightSeverity.ERROR)
      .textAttributes(SonarLintTextAttributes.SELECTED)
      .create();
  }
}
