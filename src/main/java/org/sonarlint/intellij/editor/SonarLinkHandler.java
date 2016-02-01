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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.ui.SonarLintConsole;

import java.awt.Desktop;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class SonarLinkHandler extends TooltipLinkHandler {

  public static final String UNABLE_TO_OPEN_LINK = "Unable to open link";

  public static void openWebpage(@NotNull Project p, URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
      }
    } else {
      String msg = "Launching a browser is not supported on the current platform. Unable to open '" + uri + "'";
      SonarLintConsole.get(p).error(msg);
      Messages.showMessageDialog(p, msg, "Unable to open URL", Messages.getWarningIcon());
    }
  }

  public static void openWebpage(@NotNull Project p, URL url) {
    try {
      openWebpage(p, url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
    }
  }

  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    return handleLink(editor.getProject(), refSuffix);
  }

  public static boolean handleLink(@NotNull Project p, @NotNull String refSuffix) {
    try {
      openWebpage(p, new URL(ruleDescriptionUrl(refSuffix, "https://update.sonarlint.org")));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
    }
    return true;
  }

  public static String ruleDescriptionUrl(String ruleKey, String serverUrl) {
    String urlTemplate = "%s/rules/show/%s?layout=false";

    return String.format(urlTemplate, serverUrl, ruleKey);
  }

  @Nullable
  @Override
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return null;
  }
}
