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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.components.ProjectComponent;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.IssueListener;

import java.util.List;
import java.util.Map;

public interface SonarLintFacade extends ProjectComponent {
  void startAnalysis(List<AnalysisConfiguration.InputFile> inputFiles, IssueListener issueListener, Map<String, String> additionalProps);

  void stop();
}
