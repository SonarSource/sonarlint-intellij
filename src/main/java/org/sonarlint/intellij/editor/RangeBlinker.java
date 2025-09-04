/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.util.ArrayUtil;
import java.util.ArrayList;
import java.util.List;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

/**
 * Based on {@link com.intellij.util.ui.RangeBlinker}, but stops immediately when requested
 * and uses a shared alarm for efficiency
 */
public class RangeBlinker {
  private final Editor myEditor;
  private int myTimeToLive;
  private final List<Segment> myMarkers = new ArrayList<>();
  private boolean show = true;
  private final TextAttributes myAttributes;
  private final List<RangeHighlighter> myAddedHighlighters = new ArrayList<>();
  private final String blinkerId;
  private final Project project;

  public RangeBlinker(Editor editor, final TextAttributes attributes, int timeToLive, Project project) {
    myAttributes = attributes;
    myEditor = editor;
    myTimeToLive = timeToLive;
    this.blinkerId = "blinker_" + System.identityHashCode(this);
    this.project = project;
  }

  public void blinkHighlights(final List<HighlightInfo> markers) {
    removeHighlights();
    myMarkers.clear();
    myMarkers.addAll(markers);
    show = true;
    getService(project, SharedBlinkManager.class).registerBlinker(blinkerId, this);
  }

  /**
   * Called by SharedBlinkManager to perform one blink cycle.
   * @return true if blinking should continue, false if it should stop
   */
  public boolean performBlinkCycle() {
    if (myEditor.isDisposed() || (project != null && project.isDisposed())) {
      return false;
    }

    var markupModel = myEditor.getMarkupModel();
    if (show) {
      for (var segment : myMarkers) {
        if (segment.getEndOffset() > myEditor.getDocument().getTextLength()) {
          continue;
        }
        var highlighter = markupModel.addRangeHighlighter(segment.getStartOffset(), segment.getEndOffset(),
          HighlighterLayer.ADDITIONAL_SYNTAX, myAttributes,
          HighlighterTargetArea.EXACT_RANGE);
        myAddedHighlighters.add(highlighter);
      }
    } else {
      removeHighlights();
    }
    
    // Check if we should continue blinking
    if (myTimeToLive > 0 || show) {
      myTimeToLive--;
      show = !show;
      return true;
    } else {
      return false;
    }
  }

  public void stopBlinking() {
    myTimeToLive = 0;
    getService(project, SharedBlinkManager.class).unregisterBlinker(blinkerId);
    removeHighlights();
  }

  void removeHighlights() {
    var markupModel = myEditor.getMarkupModel();
    var allHighlighters = markupModel.getAllHighlighters();

    myAddedHighlighters.stream()
      .filter(h -> !ArrayUtil.contains(allHighlighters, h))
      .forEach(RangeHighlighter::dispose);
    myAddedHighlighters.clear();
  }

}
