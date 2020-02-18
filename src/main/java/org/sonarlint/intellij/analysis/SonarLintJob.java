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
package org.sonarlint.intellij.analysis;

import com.google.common.base.Preconditions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.trigger.TriggerType;

public class SonarLintJob {
  private final Map<Module, Collection<VirtualFile>> files;
  private final TriggerType trigger;
  private final long creationTime;
  private final boolean waitForServerIssues;
  private final Project project;
  private final Collection<VirtualFile> filesToClearIssues;
  @Nullable private final AnalysisCallback callback;

  SonarLintJob(Module module, Collection<VirtualFile> files, Collection<VirtualFile> filesToClearIssues, TriggerType trigger) {
    this(module.getProject(), Collections.singletonMap(module, files), filesToClearIssues, trigger, false, null);
  }

  SonarLintJob(Project project, Map<Module, Collection<VirtualFile>> files, Collection<VirtualFile> filesToClearIssues, TriggerType trigger, boolean waitForServerIssues, @Nullable AnalysisCallback callback) {
    this.project = project;
    this.filesToClearIssues = Collections.unmodifiableCollection(filesToClearIssues);
    this.callback = callback;
    this.waitForServerIssues = waitForServerIssues;
    Preconditions.checkNotNull(files);
    Preconditions.checkNotNull(trigger);

    this.files = Collections.unmodifiableMap(new HashMap<>(files));
    this.trigger = trigger;
    this.creationTime = System.currentTimeMillis();
  }

  public Project project() {
    return project;
  }

  @CheckForNull
  public AnalysisCallback callback() {
    return callback;
  }

  public long creationTime() {
    return creationTime;
  }

  public Map<Module, Collection<VirtualFile>> filesPerModule() {
    return files;
  }

  public Stream<VirtualFile> allFiles() {
    return files.entrySet().stream()
      .flatMap(e -> e.getValue().stream());
  }

  public boolean waitForServerIssues() {
    return waitForServerIssues;
  }

  public TriggerType trigger() {
    return trigger;
  }

  public Collection<VirtualFile> filesToClearIssues() {
    return filesToClearIssues;
  }
}
