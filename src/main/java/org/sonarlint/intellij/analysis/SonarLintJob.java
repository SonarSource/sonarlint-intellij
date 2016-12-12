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
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.trigger.TriggerType;

public class SonarLintJob {
  private final Module m;
  private final Set<VirtualFile> files;
  private final TriggerType trigger;
  private final long creationTime;
  @Nullable private final AnalysisCallback callback;

  SonarLintJob(Module m, Collection<VirtualFile> files, TriggerType trigger) {
    this(m, files, trigger, null);
  }

  SonarLintJob(Module m, Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisCallback callback) {
    this.callback = callback;
    Preconditions.checkNotNull(m);
    Preconditions.checkNotNull(trigger);
    Preconditions.checkArgument(!files.isEmpty(), "List of files is empty");

    this.m = m;
    Set<VirtualFile> fileSet = new HashSet<>();
    fileSet.addAll(files);
    this.files = Collections.unmodifiableSet(fileSet);
    this.trigger = trigger;
    this.creationTime = System.currentTimeMillis();
  }

  @CheckForNull
  public AnalysisCallback callback() {
    return callback;
  }

  public long creationTime() {
    return creationTime;
  }

  public Module module() {
    return m;
  }

  public Set<VirtualFile> files() {
    return files;
  }

  public TriggerType trigger() {
    return trigger;
  }

}
