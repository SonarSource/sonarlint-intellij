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
package org.sonarlint.intellij.config.global;

import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SonarQubeServerEditorPanelTest extends SonarTest {
  private SonarQubeServerEditorPanel panel;
  private SonarQubeServer server;
  private Consumer<SonarQubeServer> consumer;

  @Before
  public void setUp() {
    super.setUp();
    consumer = mock(Consumer.class);
    server = createServer();

    panel = new SonarQubeServerEditorPanel(consumer, server);
    HttpConfigurable httpConfigurable = mock(HttpConfigurable.class);
    super.register(app, HttpConfigurable.class, httpConfigurable);
  }

  @Test
  public void testRoundTrip() {
    panel.create();
    panel.apply();

    assertThat(server.getHostUrl()).isEqualTo("host");
    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getName()).isEqualTo("name");
    assertThat(server.getLogin()).isEqualTo("login");
    assertThat(server.getStorageId()).isEqualTo("id");

    verify(consumer).consume(server);
    verifyNoMoreInteractions(consumer);
  }

  @Test
  public void testSave() {
    SonarQubeServer spy = Mockito.spy(server);
    panel = new SonarQubeServerEditorPanel(consumer, spy);
    panel.create();
    panel.apply();

    verify(spy).setName("name");
    verify(spy).setHostUrl("host");
    verify(spy).setStorageId("id");
    verify(spy).setLogin("login");
    verify(spy).setPassword("pass");
  }

  @Test
  public void testEmptyServer() {
    panel = new SonarQubeServerEditorPanel(consumer, new SonarQubeServer());
    panel.create();
    panel.apply();
  }

  private static SonarQubeServer createServer() {
    SonarQubeServer server = new SonarQubeServer();
    server.setHostUrl("host");
    server.setPassword("pass");
    server.setName("name");
    server.setLogin("login");
    server.setStorageId("id");
    return server;
  }
}
