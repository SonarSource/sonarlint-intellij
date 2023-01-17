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
package org.sonarlint.intellij.messages;

import com.intellij.util.messages.Topic;
import org.sonarlint.intellij.analysis.AnalysisRequest;

/**
 * Notifies about analysis tasks starting. It will be called for any analysis task, regardless of the trigger, if it is background or not, etc.
 */
public interface AnalysisListener {
  Topic<AnalysisListener> TOPIC = Topic.create("SonarLint analysis start", AnalysisListener.class);

  void started(AnalysisRequest analysisRequest);

  abstract class Adapter implements AnalysisListener {
    @Override
    public void started(AnalysisRequest analysisRequest) {
      // can be optionally implemented
    }
  }
}
