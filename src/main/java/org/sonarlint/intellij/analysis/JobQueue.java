/**
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
import com.intellij.openapi.project.Project;
import java.util.Deque;
import java.util.LinkedList;
import org.jetbrains.annotations.Nullable;

/**
 * NOT thread safe
 */
public class JobQueue {
  public static final int CAPACITY = 5;
  private final Project project;
  private final Deque<SonarLintJobManager.SonarLintJob> queue;

  public JobQueue(Project project) {
    this.project = project;
    this.queue = new LinkedList<>();
  }

  public void queue(SonarLintJobManager.SonarLintJob job, boolean optimize) throws NoCapacityException {
    Preconditions.checkArgument(job.module().getProject().equals(project), "job belongs to a different project");
    Preconditions.checkArgument(!job.files().isEmpty(), "no files to analyze");

    if (optimize && tryAddToExisting(job)) {
      return;
    }

    if (queue.size() >= CAPACITY) {
      throw new NoCapacityException();
    }
    queue.addLast(job);
  }

  private boolean tryAddToExisting(SonarLintJobManager.SonarLintJob job) {
    if (queue.isEmpty()) {
      return false;
    }

    for (SonarLintJobManager.SonarLintJob j : queue) {
      if (!j.module().equals(job.module())) {
        continue;
      }

      j.files().addAll(job.files());
      return true;
    }

    return false;
  }

  public int size() {
    return queue.size();
  }

  public void queue(SonarLintJobManager.SonarLintJob job) throws NoCapacityException {
    queue(job, true);
  }

  @Nullable
  public SonarLintJobManager.SonarLintJob get() {
    if (queue.isEmpty()) {
      return null;
    }
    return queue.removeFirst();
  }

  public void clear() {
    queue.clear();
  }

  public static class NoCapacityException extends Exception {

  }
}
