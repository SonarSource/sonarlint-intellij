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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "SonarLintProjectState", storages = {@Storage("sonarlint-state.xml")})
public final class SonarLintProjectState implements PersistentStateComponent<SonarLintProjectState> {
  // Xml serializer doesn't handle ZonedDateTime, so we keep milliseconds since epoch
  @Tag
  private Long lastEventPolling = null;

  @CheckForNull
  public ZonedDateTime getLastEventPolling() {
    if (lastEventPolling != null) {
      return ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastEventPolling), ZoneId.systemDefault());
    }
    return null;
  }

  public void setLastEventPolling(ZonedDateTime dateTime) {
    this.lastEventPolling = dateTime.toInstant().toEpochMilli();
  }

  @Nullable @Override public SonarLintProjectState getState() {
    return this;
  }

  @Override public void loadState(SonarLintProjectState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
