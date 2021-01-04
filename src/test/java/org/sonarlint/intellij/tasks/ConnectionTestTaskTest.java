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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.global.ServerConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ConnectionTestTaskTest extends AbstractSonarLintLightTests {

  @Test
  public void fail_if_no_connection() {
    ServerConnection server = ServerConnection.newBuilder().setHostUrl("invalid_url").build();
    ConnectionTestTask task = new ConnectionTestTask(server);
    ProgressIndicator progress = mock(ProgressIndicator.class);
    task.run(progress);
    verify(progress).setIndeterminate(true);

    assertThat(task.getException()).isNotNull();
    assertThat(task.result()).isNull();
  }
}
