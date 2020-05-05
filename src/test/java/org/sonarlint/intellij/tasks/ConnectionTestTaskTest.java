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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import org.junit.Test;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.GlobalLogOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ConnectionTestTaskTest {
  private GlobalLogOutput globalLogOutput = mock(GlobalLogOutput.class);

  @Test
  public void fail_if_no_connection() {
    SonarQubeServer server = SonarQubeServer.newBuilder().setHostUrl("invalid_url").build();
    ConnectionTestTask task = new ConnectionTestTask(server, globalLogOutput);
    ProgressIndicator progress = mock(ProgressIndicator.class);
    task.run(progress);
    verify(progress).setIndeterminate(true);

    assertThat(task.getException()).isNotNull();
    assertThat(task.result()).isNull();
    verify(globalLogOutput).logError(eq("Connection test failed"), any(Throwable.class));
  }
}
