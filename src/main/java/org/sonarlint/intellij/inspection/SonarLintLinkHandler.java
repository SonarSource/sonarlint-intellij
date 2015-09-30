/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.Desktop;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;
import org.sonarlint.intellij.config.SonarLintGlobalSettings;

public class SonarLintLinkHandler extends TooltipLinkHandler {


  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    SonarLintGlobalSettings settings = SonarLintGlobalSettings.getInstance();
    try {
      openWebpage(new URL(ruleDescriptionUrl(refSuffix, settings.getServerUrl())));
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return true;
  }

  public String ruleDescriptionUrl(String ruleKey, String serverUrl) {
    String urlTemplate = "%s/rules/show/%s?layout=false";

    return String.format(urlTemplate, serverUrl, ruleKey);
  }

  public static void openWebpage(URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void openWebpage(URL url) {
    try {
      openWebpage(url.toURI());
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  @Nullable
  @Override
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return null;
  }
}
