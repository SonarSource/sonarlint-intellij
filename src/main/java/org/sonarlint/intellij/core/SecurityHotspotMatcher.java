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
package org.sonarlint.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.finding.TextRangeMatcher;
import org.sonarlint.intellij.finding.Location;
import org.sonarlint.intellij.finding.hotspot.LocalHotspot;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;

import static org.sonarlint.intellij.finding.LocationKt.fileOnlyLocation;
import static org.sonarlint.intellij.finding.LocationKt.resolvedLocation;
import static org.sonarlint.intellij.finding.LocationKt.unknownLocation;

public class SecurityHotspotMatcher {

  private final Project project;
  private final TextRangeMatcher textRangeMatcher;

  public SecurityHotspotMatcher(Project project) {
    this.project = project;
    textRangeMatcher = new TextRangeMatcher(project);
  }

  public LocalHotspot match(ServerHotspotDetails serverHotspot) {
    return new LocalHotspot(matchLocation(serverHotspot), serverHotspot);
  }

  public Location matchLocation(ServerHotspotDetails serverHotspot) {
    for (var contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      if (contentRoot.isDirectory()) {
        var matchedFile = contentRoot.findFileByRelativePath(serverHotspot.filePath);
        if (matchedFile != null) {
          return matchTextRange(matchedFile, serverHotspot.textRange, serverHotspot.message);
        }
      } else {
        // On Rider, all source files are returned as individual content roots, so simply check for equality
        if (contentRoot.getPath().endsWith(serverHotspot.filePath)) {
          return matchTextRange(contentRoot, serverHotspot.textRange, serverHotspot.message);
        }
      }
    }
    return unknownLocation(serverHotspot.message, serverHotspot.filePath);
  }

  private Location matchTextRange(VirtualFile matchedFile, TextRange textRange, String message) {
    try {
      var rangeMarker = textRangeMatcher.match(matchedFile, textRange);
      return resolvedLocation(matchedFile, rangeMarker, message, null);
    } catch (TextRangeMatcher.NoMatchException e) {
      return fileOnlyLocation(matchedFile, message);
    }
  }

}
