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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobQueueTest {
  private JobQueue queue;
  private Project project;
  private Module module;
  private Set<VirtualFile> files;

  @Before
  public void setUp() {
    project = mock(Project.class);
    module = mock(Module.class);
    files = new HashSet<>();
    files.add(mock(VirtualFile.class));
    when(module.getProject()).thenReturn(project);
    queue = new JobQueue(project);
  }

  @Test(expected = JobQueue.NoCapacityException.class)
  public void dontPassCapacity() throws JobQueue.NoCapacityException {
    for (int i = 0; i <= JobQueue.CAPACITY; i++) {
      queue.queue(createJobNewFiles(1), false);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void dontAnalyzeDifferentProject() throws JobQueue.NoCapacityException {
    Project p = mock(Project.class);
    when(module.getProject()).thenReturn(p);
    queue.queue(createJobNewFiles(1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void dontAnalyzeEmpty() throws JobQueue.NoCapacityException {
    queue.queue(new SonarLintJobManager.SonarLintJob(module, Collections.emptySet(), TriggerType.ACTION));
  }

  @Test
  public void dontOptimize() throws JobQueue.NoCapacityException {
    SonarLintJobManager.SonarLintJob job = createJob();

    for (int i = 0; i < 3; i++) {
      queue.queue(job, false);
    }

    assertThat(queue.size()).isEqualTo(3);

    SonarLintJobManager.SonarLintJob j;

    for (int i = 0; i < 3; i++) {
      j = queue.get();
      assertThat(j).isEqualTo(job);
    }
  }

  @Test
  public void optimize() throws JobQueue.NoCapacityException {
    for (int i = 0; i < 3; i++) {
      queue.queue(createJobNewFiles(2), true);
    }

    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.get().files()).hasSize(6);
  }

  @Test
  public void dontRepeatSameFile() throws JobQueue.NoCapacityException {
    for (int i = 0; i < 3; i++) {
      queue.queue(createJob(), true);
    }

    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.get().files()).hasSize(1);
  }

  @Test
  public void handleDifferentModules() throws JobQueue.NoCapacityException {
    for (int i = 0; i < 3; i++) {
      queue.queue(createJobNewModule(), true);
    }

    assertThat(queue.size()).isEqualTo(3);
    assertThat(queue.get().files()).hasSize(1);
  }

  private SonarLintJobManager.SonarLintJob createJobNewFiles(int numFiles) {
    Set<VirtualFile> files = new HashSet<>(numFiles);

    for (int i = 0; i < numFiles; i++) {
      files.add(mock(VirtualFile.class));
    }

    return new SonarLintJobManager.SonarLintJob(module, files, TriggerType.ACTION);
  }

  private SonarLintJobManager.SonarLintJob createJobNewModule() {
    Module module = mock(Module.class);
    when(module.getProject()).thenReturn(project);
    return new SonarLintJobManager.SonarLintJob(module, files, TriggerType.ACTION);
  }

  private SonarLintJobManager.SonarLintJob createJob() {
    return new SonarLintJobManager.SonarLintJob(module, files, TriggerType.ACTION);
  }
}
