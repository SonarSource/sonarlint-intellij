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
import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.Immutable;
import org.sonarlint.intellij.trigger.TriggerType;

@Immutable
public class SonarLintJob {
  private final Module m;
  private final Set<VirtualFile> files;
  private final TriggerType trigger;
  private final long creationTime;
  private final CompletableFuture<AnalysisResult> future;

  SonarLintJob(Module m, Collection<VirtualFile> files, TriggerType trigger, CompletableFuture<AnalysisResult> future) {
    this.future = future;
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

  public CompletableFuture<AnalysisResult> future() {
    return future;
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
