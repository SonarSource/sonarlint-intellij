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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Based on {@link com.intellij.util.ui.RangeBlinker}, but stops immediately when requested
 */
public class RangeBlinker {
  private final Editor myEditor;
  private int myTimeToLive;
  private final List<Segment> myMarkers = new ArrayList<>();
  private boolean show = true;
  private final Alarm myBlinkingAlarm = new Alarm();
  private final TextAttributes myAttributes;
  private final List<RangeHighlighter> myAddedHighlighters = new ArrayList<>();

  public RangeBlinker(Editor editor, final TextAttributes attributes, int timeToLive) {
    myAttributes = attributes;
    myEditor = editor;
    myTimeToLive = timeToLive;
  }

  public void resetMarkers(final List<Segment> markers) {
    removeHighlights();
    myMarkers.clear();
    myBlinkingAlarm.cancelAllRequests();
    myMarkers.addAll(markers);
    show = true;
  }

  private void removeHighlights() {
    MarkupModel markupModel = myEditor.getMarkupModel();
    RangeHighlighter[] allHighlighters = markupModel.getAllHighlighters();

    myAddedHighlighters.stream()
      .filter(h -> !ArrayUtil.contains(allHighlighters, h))
      .forEach(RangeHighlighter::dispose);
    myAddedHighlighters.clear();
  }

  public void startBlinking() {
    Project project = myEditor.getProject();
    if (ApplicationManager.getApplication().isDisposed() || myEditor.isDisposed() || (project != null && project.isDisposed())) {
      return;
    }

    MarkupModel markupModel = myEditor.getMarkupModel();
    if (show) {
      for (Segment segment : myMarkers) {
        if (segment.getEndOffset() > myEditor.getDocument().getTextLength()) {
          continue;
        }
        RangeHighlighter highlighter = markupModel.addRangeHighlighter(segment.getStartOffset(), segment.getEndOffset(),
          HighlighterLayer.ADDITIONAL_SYNTAX, myAttributes,
          HighlighterTargetArea.EXACT_RANGE);
        myAddedHighlighters.add(highlighter);
      }
    } else {
      removeHighlights();
    }
    myBlinkingAlarm.cancelAllRequests();
    myBlinkingAlarm.addRequest(() -> {
      if (myTimeToLive > 0 || show) {
        myTimeToLive--;
        show = !show;
        startBlinking();
      }
    }, 400);
  }

  public void stopBlinking() {
    myTimeToLive = 0;
    myBlinkingAlarm.cancelAllRequests();
    removeHighlights();
  }
}
