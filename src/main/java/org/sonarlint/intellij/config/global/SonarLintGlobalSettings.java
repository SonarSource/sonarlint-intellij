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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.SonarLintBundle;

import java.io.File;

@State(name = "SonarLintGlobalSettings", storages = {@Storage(id = "sonarlint", file = StoragePathMacros.APP_CONFIG + "/sonarlint.xml")})
public final class SonarLintGlobalSettings implements PersistentStateComponent<SonarLintGlobalSettings>, ExportableApplicationComponent {

  private boolean autoTrigger = true;

  public static SonarLintGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  @Override
  public SonarLintGlobalSettings getState() {
    return this;
  }

  @Override
  public void loadState(SonarLintGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[] {PathManager.getOptionsFile("sonarlint")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return SonarLintBundle.message("sonarlint.settings");
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "SonarLintGlobalSettings";
  }

  @Override
  public void initComponent() {
    // Nothing to do
  }

  @Override
  public void disposeComponent() {
    // Nothing to do
  }

  public boolean isAutoTrigger() {
    return autoTrigger;
  }

  public void setAutoTrigger(boolean autoTrigger) {
    this.autoTrigger = autoTrigger;
  }
}
