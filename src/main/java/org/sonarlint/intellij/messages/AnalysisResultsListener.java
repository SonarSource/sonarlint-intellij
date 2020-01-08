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
package org.sonarlint.intellij.messages;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.issue.LiveIssue;

@FunctionalInterface
public interface AnalysisResultsListener {
  Topic<AnalysisResultsListener> ANALYSIS_RESULTS_TOPIC = Topic.create("Analysis results", AnalysisResultsListener.class);

  /**
   * Called when the store of issues is modified. It is modified only as a result of a user action to analyze files.
   */
  void update(Map<VirtualFile, Collection<LiveIssue>> issues);
}
