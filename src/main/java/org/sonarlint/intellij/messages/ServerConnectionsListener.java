/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.util.messages.Topic;
import java.util.List;
import org.sonarlint.intellij.config.global.ServerConnection;

public interface ServerConnectionsListener {
  Topic<ServerConnectionsListener> TOPIC = Topic.create("Server connections events", ServerConnectionsListener.class);

  void afterChange(List<ServerConnection> allConnections);
  void credentialsChanged(List<ServerConnection> changedConnections);

  abstract class Adapter implements ServerConnectionsListener {
    @Override
    public void afterChange(List<ServerConnection> allConnections) {
      // empty implementation
    }

    @Override
    public void credentialsChanged(List<ServerConnection> changedConnections) {
      // empty implementation
    }
  }
}
