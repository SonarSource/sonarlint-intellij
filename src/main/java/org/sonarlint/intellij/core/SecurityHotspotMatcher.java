/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.Location;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

import static org.sonarlint.intellij.issue.LocationKt.fileOnlyLocation;
import static org.sonarlint.intellij.issue.LocationKt.resolvedLocation;
import static org.sonarlint.intellij.issue.LocationKt.unknownLocation;

public class SecurityHotspotMatcher {

  private final Project project;
  private final IssueMatcher issueMatcher;

  public SecurityHotspotMatcher(Project project) {
    this.project = project;
    issueMatcher = new IssueMatcher(project);
  }

  public LocalHotspot match(ServerHotspot serverHotspot) {
    return new LocalHotspot(matchLocation(serverHotspot), serverHotspot);
  }

  public Location matchLocation(ServerHotspot serverHotspot) {
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

  private Location matchTextRange(VirtualFile matchedFile, ServerHotspot.TextRange textRange, String message) {
    try {
      var rangeMarker = issueMatcher.match(matchedFile, convert(textRange));
      return resolvedLocation(matchedFile, rangeMarker, message, null);
    } catch (IssueMatcher.NoMatchException e) {
      return fileOnlyLocation(matchedFile, message);
    }
  }

  private static TextRange convert(ServerHotspot.TextRange textRange) {
    return new TextRange(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

}
