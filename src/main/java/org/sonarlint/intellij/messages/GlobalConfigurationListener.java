/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

public interface GlobalConfigurationListener {
  Topic<GlobalConfigurationListener> TOPIC = Topic.create("Global configuration events", GlobalConfigurationListener.class);

  /**
   * Called immediately when list of servers is changed (servers added or removed), even before saving
   */
  void changed(List<ServerConnection> serverList);

  /**
   * Called when settings are saved (clicking "Apply" or "Ok")
   */
  void applied(SonarLintGlobalSettings previousSettings, SonarLintGlobalSettings newSettings);

  abstract class Adapter implements GlobalConfigurationListener {
    @Override
    public void changed(List<ServerConnection> serverList) {
      // nothing done by default
    }

    @Override
    public void applied(SonarLintGlobalSettings previousSettings, SonarLintGlobalSettings newSettings) {
      // nothing done by default
    }
  }
}
