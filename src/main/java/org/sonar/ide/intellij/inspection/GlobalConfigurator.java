/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
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
package org.sonar.ide.intellij.inspection;

import com.intellij.openapi.project.Project;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.SonarQubeServer;

import java.io.File;

public class GlobalConfigurator {

  public static final String SONAR_URL = "sonar.host.url";
  public static final String SONAR_LOGIN = "sonar.login";
  public static final String SONAR_PASSWORD = "sonar.password";

  public static final String ANALYSIS_MODE = "sonar.analysis.mode";
  public static final String ANALYSIS_MODE_INCREMENTAL = "incremental";
  public static final String ANALYSIS_MODE_PREVIEW = "preview";
  public static final String REPORT_OUTPUT_PROPERTY = "sonar.report.export.path";
  public static final String VERBOSE_PROPERTY = "sonar.verbose";

  private GlobalConfigurator() {
    // Utility class
  }

  public static void configureAnalysis(Project p, File outputFile, ProjectSettings projectSettings, SonarQubeServer server, boolean debugEnabled, ParamWrapper params) {

    // Global configuration
    params.add(SONAR_URL, server.getUrl());
    if (server.hasCredentials()) {
      params.add(SONAR_LOGIN, server.getUsername());
      params.add(SONAR_PASSWORD, server.getPassword());
    }
    params.add(ANALYSIS_MODE, ANALYSIS_MODE_INCREMENTAL);

    // Output file is relative to working dir
    params.add(REPORT_OUTPUT_PROPERTY, outputFile.getName());
    if (debugEnabled) {
      params.add(VERBOSE_PROPERTY, "true");
    }
  }
}
