/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.issue.LiveIssue;

public class AnalysisCallbackAggregator implements AnalysisCallback {
  private final Map<VirtualFile, Collection<LiveIssue>> issues;
  private final AnalysisCallback callback;
  private int numJobs;
  private boolean errored = false;

  public AnalysisCallbackAggregator(AnalysisCallback callback, int numJobs) {
    this.callback = callback;
    this.numJobs = numJobs;
    this.issues = new HashMap<>();
  }

  @Override public synchronized void onSuccess(Map<VirtualFile, Collection<LiveIssue>> newIssues) {
    if (errored) {
      // if an error occurred, nothing else to do
      return;
    }
    issues.putAll(newIssues);
    numJobs--;

    if (numJobs == 0) {
      callback.onSuccess(issues);
    }

  }

  @Override public synchronized void onError(Exception e) {
    errored = true;
    // don't accept errors once we are done
    if (numJobs > 0) {
      callback.onError(e);
    }
  }
}
