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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.Immutable;
import org.sonarlint.intellij.trigger.TriggerType;

@Immutable
public class SonarLintJob {
  private final Module m;
  private final Set<VirtualFile> files;
  private final Set<TriggerType> triggers;
  private final long creationTime;

  SonarLintJob(Module m, Collection<VirtualFile> files, TriggerType trigger) {
    this(m, files, Collections.singleton(trigger));
  }

  SonarLintJob(Module m, Collection<VirtualFile> files, Set<TriggerType> triggers) {
    Preconditions.checkNotNull(m);
    Preconditions.checkNotNull(triggers);
    Preconditions.checkArgument(!files.isEmpty(), "List of files is empty");

    this.m = m;
    Set<VirtualFile> fileSet = new HashSet<>();
    fileSet.addAll(files);
    this.files = Collections.unmodifiableSet(fileSet);
    this.triggers = Collections.unmodifiableSet(triggers);
    this.creationTime = System.currentTimeMillis();
  }

  SonarLintJob(SonarLintJob job1, SonarLintJob job2) {
    Preconditions.checkArgument(job1.module().equals(job2.module()), "Jobs must belong to the same module");

    this.m = job1.module();
    SonarLintJob oldest = job1.creationTime() < job2.creationTime() ? job1 : job2;
    Set<VirtualFile> fileSet = new HashSet<>();
    fileSet.addAll(job1.files());
    fileSet.addAll(job2.files());
    this.files = Collections.unmodifiableSet(fileSet);
    this.creationTime = oldest.creationTime();
    this.triggers = Stream.concat(job1.triggers().stream(), job2.triggers().stream()).collect(Collectors.toSet());
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

  public Set<TriggerType> triggers() {
    return triggers;
  }

}
