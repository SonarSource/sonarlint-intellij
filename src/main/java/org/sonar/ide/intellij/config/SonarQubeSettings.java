/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;

import java.io.File;
import java.util.List;

@State(name = "SonarQubeSettings", storages = {@Storage(id = "sonarqube", file = "$APP_CONFIG$/sonarqube.xml")})
public final class SonarQubeSettings implements PersistentStateComponent<SonarQubeSettings>, ExportableApplicationComponent {


    public static SonarQubeSettings getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getComponent(SonarQubeSettings.class);
    }

    public java.util.List<SonarQubeServer> servers = new java.util.ArrayList<SonarQubeServer>();

  public List<SonarQubeServer> getServers() {
    return servers;
  }

  public SonarQubeSettings getState() {
        return this;
    }

    public void loadState(SonarQubeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    @NotNull
    public File[] getExportFiles() {
        return new File[]{PathManager.getOptionsFile("sonarqube")};
    }

    @NotNull
    public String getPresentableName() {
        return SonarQubeBundle.message("sonarqube.settings");
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "SonarQubeSettings";
    }

    public void initComponent() {

    }

    public void disposeComponent() {

    }

}
