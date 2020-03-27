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
package org.sonarlint.intellij.core;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintEngineFactoryTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private SonarLintEngineFactory factory;
  private GlobalLogOutput log;

  @Before
  public void setUp() {
    System.setProperty("idea.home.path", temp.getRoot().getAbsolutePath());
    SonarApplication application = mock(SonarApplication.class);
    when(application.getPluginPath()).thenReturn(temp.getRoot().getAbsoluteFile().toPath().resolve("plugins"));
    log = mock(GlobalLogOutput.class);
    factory = new SonarLintEngineFactory(application, log);
  }

  @Test
  public void standalone() {
    StandaloneSonarLintEngine engine = factory.createEngine();
    assertThat(engine).isNotNull();
    engine.stop();
    verify(log, atLeastOnce()).log(anyString(), any(LogOutput.Level.class));
  }

  @Test
  public void componentName() {
    assertThat(factory.getComponentName()).isEqualTo("SonarLintEngineFactory");
  }

  @Test
  public void connected() {
    ConnectedSonarLintEngine engine = factory.createEngine("id");
    assertThat(engine).isNotNull();
    assertThat(engine.getGlobalStorageStatus()).isNull();
    engine.stop(true);
    verify(log, atLeastOnce()).log(anyString(), any(LogOutput.Level.class));
  }
}
