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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.junit.Test;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class AnalysisRequestTest {
  @Test
  public void testRoundTrip() {
    var p = mock(Project.class);
    var f = mock(VirtualFile.class);
    var analysisRequest = new AnalysisRequest(p, List.of(f), TriggerType.COMPILATION, false, mock(AnalysisCallback.class));

    assertThat(analysisRequest.files()).containsOnly(f);
    assertThat(analysisRequest.trigger()).isEqualTo(TriggerType.COMPILATION);
  }

}
