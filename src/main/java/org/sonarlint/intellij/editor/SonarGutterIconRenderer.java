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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.SonarUrlAction;
import org.sonarlint.intellij.util.ResourceLoader;

import javax.annotation.Nullable;
import javax.swing.Icon;
import java.io.IOException;
import java.util.Objects;

public class SonarGutterIconRenderer extends GutterIconRenderer {
  private static final Logger LOGGER = Logger.getInstance(SonarGutterIconRenderer.class);
  private static final String ICON_NAME = "onde-sonar-13.png";
  private final String toolTip;
  @Nullable
  private final String url;
  private final String ruleKey;

  public SonarGutterIconRenderer(String toolTip, @Nullable String url, String ruleKey) {
    this.toolTip = toolTip;
    this.url = url;
    this.ruleKey = ruleKey;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    try {
      return ResourceLoader.getIcon(ICON_NAME);
    } catch (IOException e) {
      LOGGER.error("Couldn't load SonarLint Gutter Icon", e);
      return null;
    }
  }

  @Override
  @Nullable
  public ActionGroup getPopupMenuActions() {
    if (url == null) {
      return null;
    }
    return new DefaultActionGroup(new SonarUrlAction(url, ruleKey));
  }

  @Override
  public AnAction getClickAction() {
    return null;
  }

  @Override
  public Alignment getAlignment() {
    return Alignment.RIGHT;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public String getTooltipText() {
    return toolTip;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SonarGutterIconRenderer)) {
      return false;
    }

    SonarGutterIconRenderer other = (SonarGutterIconRenderer) obj;
    return Objects.equals(this.getTooltipText(), other.getTooltipText());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getTooltipText());
  }
}
