/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import org.sonarlint.intellij.trigger.TriggerType;

public class AnalysisRequest {
  private final Project project;
  private final Collection<VirtualFile> files;
  private final TriggerType trigger;
  private final boolean waitForServerIssues;
  private final AnalysisCallback callback;

  AnalysisRequest(Project project, Collection<VirtualFile> files, TriggerType trigger, boolean waitForServerIssues, AnalysisCallback callback) {
    this.project = project;
    this.callback = callback;
    this.waitForServerIssues = waitForServerIssues;
    Preconditions.checkNotNull(files);
    Preconditions.checkNotNull(trigger);

    this.files = Collections.unmodifiableCollection(files);
    this.trigger = trigger;
  }

  public Project project() {
    return project;
  }

  public AnalysisCallback callback() {
    return callback;
  }

  public Collection<VirtualFile> files() {
    return files;
  }

  public boolean waitForServerIssues() {
    return waitForServerIssues;
  }

  public TriggerType trigger() {
    return trigger;
  }

  public boolean isForced() {
    return trigger == TriggerType.ACTION;
  }
}
