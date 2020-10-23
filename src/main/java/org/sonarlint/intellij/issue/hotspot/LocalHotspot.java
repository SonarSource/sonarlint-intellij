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
package org.sonarlint.intellij.issue.hotspot;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;

public class LocalHotspot {
  public final Location primaryLocation;
  public final RemoteHotspot remote;

  public LocalHotspot(Location primaryLocation, RemoteHotspot remote) {
    this.primaryLocation = primaryLocation;
    this.remote = remote;
  }

  public String getMessage() {
    return remote.message;
  }

  public boolean isValid() {
    return primaryLocation.range.isValid();
  }

  public String getRuleKey() {
    return remote.rule.key;
  }

  public String getAuthor() {
    return remote.author;
  }

  public String getStatusDescription() {
    return remote.status.description + (remote.resolution == null ? "" : (" as " + remote.resolution.description));
  }

  public RemoteHotspot.Rule.Probability getProbability() {
    return remote.rule.vulnerabilityProbability;
  }

  public String getCategory() {
    return remote.rule.securityCategory;
  }

  public Integer getLineNumber() {
    return remote.textRange.getStartLine();
  }

  public static class Location {
    public final VirtualFile file;
    public final RangeMarker range;

    public Location(VirtualFile file, RangeMarker range) {
      this.file = file;
      this.range = range;
    }
  }
}
