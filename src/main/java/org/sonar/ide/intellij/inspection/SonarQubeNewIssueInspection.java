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
package org.sonar.ide.intellij.inspection;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.util.SonarQubeBundle;

public class SonarQubeNewIssueInspection extends AbstractSonarQubeInspection {

  private static final Logger LOG = Logger.getInstance(SonarQubeNewIssueInspection.class);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "SonarQube New Issue";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SonarQubeNewIssue";
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return SonarQubeBundle.message("sonarqube.inspection.newIssues.description");
  }

  @Override
  public void populateProblems(SonarQubeInspectionContext sonarQubeInspectionContext) {
    for (final ISonarIssue issue : sonarQubeInspectionContext.getLocalNewIssues()) {
      createProblem(issue);
    }
  }


}
