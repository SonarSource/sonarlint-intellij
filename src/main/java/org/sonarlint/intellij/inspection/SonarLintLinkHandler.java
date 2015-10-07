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
package org.sonarlint.intellij.inspection;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import java.awt.Desktop;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.SonarLintGlobalSettings;

public class SonarLintLinkHandler extends TooltipLinkHandler {

  public static final String UNABLE_TO_OPEN_LINK = "Unable to open link";

  public static void openWebpage(URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
      }
    }
  }

  public static void openWebpage(URL url) {
    try {
      openWebpage(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
    }
  }

  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    SonarLintGlobalSettings settings = SonarLintGlobalSettings.getInstance();
    try {
      openWebpage(new URL(ruleDescriptionUrl(refSuffix, settings.getServerUrl())));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(UNABLE_TO_OPEN_LINK, e);
    }
    return true;
  }

  public String ruleDescriptionUrl(String ruleKey, String serverUrl) {
    String urlTemplate = "%s/rules/show/%s?layout=false";

    return String.format(urlTemplate, serverUrl, ruleKey);
  }

  @Nullable
  @Override
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return null;
  }
}
