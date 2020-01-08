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
package org.sonarlint.intellij.messages;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalConfigurationListenerTest extends SonarTest {
  private List<SonarQubeServer> testList = new LinkedList<>();

  @Before
  public void prepare() {
    testList.add(SonarQubeServer.newBuilder().setName("name").build());
  }

  @Test
  public void testChanged() {
    List<SonarQubeServer> servers = new LinkedList<>();
    GlobalConfigurationListener listener = new GlobalConfigurationListener.Adapter() {
      @Override
      public void changed(List<SonarQubeServer> serverList) {
        servers.addAll(serverList);
      }
    };

    project.getMessageBus().connect().subscribe(GlobalConfigurationListener.TOPIC, listener);
    project.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC).changed(testList);
    assertThat(servers).isEqualTo(testList);
  }

  @Test
  public void testApplied() {
    List<SonarQubeServer> servers = new LinkedList<>();
    AtomicBoolean bool = new AtomicBoolean(false);

    GlobalConfigurationListener listener = new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings settings) {
        servers.addAll(settings.getSonarQubeServers());
        bool.set(settings.isAutoTrigger());
      }
    };

    project.getMessageBus().connect().subscribe(GlobalConfigurationListener.TOPIC, listener);
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    settings.setSonarQubeServers(testList);
    settings.setAutoTrigger(true);
    project.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC).applied(settings);

    assertThat(servers).isEqualTo(testList);
    assertThat(bool.get()).isTrue();
  }
}
