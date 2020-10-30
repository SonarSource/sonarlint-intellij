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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;

public class SecurityHotspotMatcher {

  private final Project project;
  private final IssueMatcher issueMatcher;

  public SecurityHotspotMatcher(Project project) {
    this.project = project;
    issueMatcher = new IssueMatcher(project);
  }

  public LocalHotspot match(RemoteHotspot remoteHotspot) throws IssueMatcher.NoMatchException {
    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project)
      .getContentRoots()) {
      VirtualFile matchedFile = contentRoot.findFileByRelativePath(remoteHotspot.filePath);
      if (matchedFile != null) {
        RangeMarker rangeMarker = issueMatcher.match(matchedFile, remoteHotspot.textRange);
        return new LocalHotspot(new LocalHotspot.Location(matchedFile, rangeMarker), remoteHotspot);
      }
    }

    throw new IssueMatcher.NoMatchException("Cannot match file location");
  }

}
