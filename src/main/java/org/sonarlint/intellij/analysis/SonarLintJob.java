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

import com.google.common.base.Preconditions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.trigger.TriggerType;

public class SonarLintJob {
  private final Map<Module, Collection<VirtualFile>> files;
  private final TriggerType trigger;
  private final long creationTime;
  private final boolean waitForServerIssues;
  @Nullable private final AnalysisCallback callback;

  SonarLintJob(Module module, Collection<VirtualFile> files, TriggerType trigger) {
    this(Collections.singletonMap(module, files), trigger, false, null);
  }

  SonarLintJob(Map<Module, Collection<VirtualFile>> files, TriggerType trigger, boolean waitForServerIssues, @Nullable AnalysisCallback callback) {
    this.callback = callback;
    this.waitForServerIssues = waitForServerIssues;
    Preconditions.checkNotNull(files);
    Preconditions.checkNotNull(trigger);
    Preconditions.checkArgument(!files.isEmpty(), "List of files is empty");

    this.files = Collections.unmodifiableMap(new HashMap<>(files));
    this.trigger = trigger;
    this.creationTime = System.currentTimeMillis();
  }

  public Project project() {
    return files.keySet().iterator().next().getProject();
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

  public Collection<VirtualFile> allFiles() {
    return files.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .collect(Collectors.toList());
  }

  public boolean waitForServerIssues() {
    return waitForServerIssues;
  }

  public TriggerType trigger() {
    return trigger;
  }

}
