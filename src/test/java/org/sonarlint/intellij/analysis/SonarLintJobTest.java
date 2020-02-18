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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Test;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SonarLintJobTest {
  @Test
  public void testRoundTrip() {
    Module m = mock(Module.class);
    VirtualFile f = mock(VirtualFile.class);
    VirtualFile toClear = mock(VirtualFile.class);
    SonarLintJob job = new SonarLintJob(m, Collections.singleton(f), Collections.singleton(toClear), TriggerType.COMPILATION);

    assertThat(job.allFiles()).containsOnly(f);
    assertThat(job.filesToClearIssues()).containsOnly(toClear);
    assertThat(job.filesPerModule().keySet()).containsOnly(m);
    assertThat(job.trigger()).isEqualTo(TriggerType.COMPILATION);
    assertThat(job.creationTime()).isBetween(System.currentTimeMillis() - 5000, System.currentTimeMillis());
  }

}
