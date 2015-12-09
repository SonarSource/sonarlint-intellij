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

import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SonarLintStatusTest {
    private SonarLintStatus status;

    @Before
    public void setUp() {
        Project p = mock(Project.class);
        status = new SonarLintStatus(p);
    }

    @Test
    public void initial_status() {
        assertStatus(false, false);
    }

    @Test
    public void run() {
        assertThat(status.tryRun()).isTrue();
        assertStatus(true, false);
    }

    @Test
    public void run_twice() {
        assertThat(status.tryRun()).isTrue();
        assertThat(status.tryRun()).isFalse();
        assertStatus(true, false);
    }

    @Test
    public void cancel_not_running() {
        status.cancel();
        assertStatus(false, false);
    }

    @Test
    public void cancel_running() {
        status.tryRun();
        status.cancel();
        assertStatus(true, true);

        status.cancel();
        assertStatus(true, true);
    }

    @Test
    public void stop_not_running() {
        status.stopRun();
        assertStatus(false, false);
    }

    @Test
    public void stop_running() {
        status.tryRun();
        status.stopRun();
        assertStatus(false, false);
    }

    @Test
    public void stop_cancel() {
        status.tryRun();
        status.cancel();
        status.stopRun();
        assertStatus(false, false);
    }

    @Test
    public void listener() {
        SonarLintStatus.Listener listener = mock(SonarLintStatus.Listener.class);
        status.subscribe(listener);

        status.tryRun();
        verify(listener).callback(SonarLintStatus.Status.RUNNING);

        status.cancel();
        verify(listener).callback(SonarLintStatus.Status.CANCELLING);

        status.stopRun();
        verify(listener).callback(SonarLintStatus.Status.STOPPED);

        verifyNoMoreInteractions(listener);
    }

    private void assertStatus(boolean running, boolean canceled) {
        assertThat(status.isRunning()).isEqualTo(running);
        assertThat(status.isCanceled()).isEqualTo(canceled);
    }


}
