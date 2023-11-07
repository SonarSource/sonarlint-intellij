/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.util.GlobalLogOutputTestImpl;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintEngineFactoryTests extends AbstractSonarLintLightTests {
  private SonarLintEngineFactory factory;
  private GlobalLogOutputTestImpl log;

  @BeforeEach
  void before() {
    System.setProperty("idea.home.path", getHomePath());
    log = new GlobalLogOutputTestImpl();
    factory = new SonarLintEngineFactory();
  }

  @Test
  void standalone() {
    var engine = factory.createStandaloneEngine();
    assertThat(engine).isNotNull();

    engine.stop();

    assertTrue(StringUtils.isEmpty(log.getLastMsg()));
  }

  @Test
  void connected() {
    var engine = factory.createEngineForConnection("id");
    assertThat(engine).isNotNull();
    engine.stop();
  }
}
