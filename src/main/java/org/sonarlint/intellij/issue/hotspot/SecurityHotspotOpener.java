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

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Optional;
import org.sonarlint.intellij.actions.IssuesViewTabOpener;
import org.sonarlint.intellij.core.SecurityHotspotMatcher;
import org.sonarlint.intellij.editor.SonarLintHighlighting;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

import static org.sonarlint.intellij.util.SonarLintUtils.getService;

public class SecurityHotspotOpener {
  private final ServerConfiguration serverConfiguration;
  private final SecurityHotspotMatcher securityHotspotMatcher;
  private final WsHelper wsHelper;

  public SecurityHotspotOpener(ServerConfiguration serverConfiguration, SecurityHotspotMatcher securityHotspotMatcher) {
    this(serverConfiguration, securityHotspotMatcher, new WsHelperImpl());
  }

  SecurityHotspotOpener(ServerConfiguration serverConfiguration, SecurityHotspotMatcher securityHotspotMatcher, WsHelper wsHelper) {
    this.serverConfiguration = serverConfiguration;
    this.securityHotspotMatcher = securityHotspotMatcher;
    this.wsHelper = wsHelper;
  }

  public void open(Project project, String hotspotKey, String projectKey) {
    Optional<RemoteHotspot> optionalRemoteHotspot = wsHelper.getHotspot(serverConfiguration, new GetSecurityHotspotRequestParams(hotspotKey, projectKey));
    if (!optionalRemoteHotspot.isPresent()) {
      return;
    }
    RemoteHotspot remoteHotspot = optionalRemoteHotspot.get();
    try {
      LocalHotspot localHotspot = securityHotspotMatcher.match(remoteHotspot);
      open(project, localHotspot.primaryLocation);
      getService(project, IssuesViewTabOpener.class).show(localHotspot);
      getService(project, SonarLintHighlighting.class).highlight(localHotspot);
    } catch (IssueMatcher.NoMatchException e) {
      Messages.showErrorDialog("The source code does not match", "Error Fetching Security Hotspot");
    }
  }

  private static void open(Project project, LocalHotspot.Location location) {
    new OpenFileDescriptor(project, location.file, location.range.getStartOffset())
      .navigate(true);
  }
}
