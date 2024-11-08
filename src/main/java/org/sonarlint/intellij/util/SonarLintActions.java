/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.components.Service;
import com.intellij.serviceContainer.NonInjectable;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.actions.ClearCurrentFileIssuesAction;
import org.sonarlint.intellij.actions.ClearReportAction;
import org.sonarlint.intellij.actions.FilterSecurityHotspotActionGroup;
import org.sonarlint.intellij.actions.RestartBackendAction;
import org.sonarlint.intellij.actions.SonarAnalyzeAllFilesAction;
import org.sonarlint.intellij.actions.SonarAnalyzeChangedFilesAction;
import org.sonarlint.intellij.actions.SonarAnalyzeFilesAction;
import org.sonarlint.intellij.actions.SonarCleanConsoleAction;
import org.sonarlint.intellij.actions.filters.IncludeResolvedFindingsAction;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;

/**
 * Creates and keeps a single instance of actions used by SonarLint.
 * Some actions are created programmatically instead of being declared in plugin.xml so that they are not registered in
 * ActionManager, becoming accessible from the action search.
 */
@Service(Service.Level.APP)
public final class SonarLintActions {

  private final AnAction clearReportAction;
  private final AnAction clearIssuesAction;
  private final AnAction cleanConsoleAction;
  private final AnAction cancelAction;
  private final AnAction configureAction;
  private final AnAction analyzeChangedFilesAction;
  private final AnAction analyzeAllFilesAction;
  private final FilterSecurityHotspotActionGroup filterAction;
  private final IncludeResolvedFindingsAction<LiveSecurityHotspot> includeResolvedHotspotsAction;
  private final IncludeResolvedFindingsAction<LiveIssue> includeResolvedIssuesAction;
  private final IncludeResolvedFindingsAction<LocalTaintVulnerability> includeResolvedTaintVulnerabilitiesAction;
  private final AnAction analyzeCurrentFileAction;
  private final AnAction restartSonarLintAction;

  public SonarLintActions() {
    this(ActionManager.getInstance());
  }

  @NonInjectable
  SonarLintActions(ActionManager actionManager) {
    var analyzeMenu = actionManager.getAction(IdeActions.GROUP_ANALYZE);
    // some flavors of IDEA don't have the Analyze menu, register at runtime to avoid error if declared in plugin.xml
    if (analyzeMenu instanceof DefaultActionGroup analyzeMenuGroup) {
      var sonarLintAnalyzeMenu = actionManager.getAction("SonarLint.AnalyzeMenu");
      analyzeMenuGroup.add(sonarLintAnalyzeMenu);
    }

    cancelAction = actionManager.getAction("SonarLint.toolwindow.Cancel");
    configureAction = actionManager.getAction("SonarLint.toolwindow.Configure");

    clearReportAction = new ClearReportAction("Clear Project Files Issues",
      "Clear analysis results",
      SonarLintIcons.CLEAN);
    clearIssuesAction = new ClearCurrentFileIssuesAction("Clear SonarLint Issues",
      "Clear SonarQube for IntelliJ issues",
      SonarLintIcons.CLEAN);
    cleanConsoleAction = new SonarCleanConsoleAction("Clear SonarLint Console",
      "Clear SonarQube for IntelliJ console",
      SonarLintIcons.CLEAN);
    analyzeAllFilesAction = new SonarAnalyzeAllFilesAction("Analyze All Project Files",
      "Run a SonarQube for IntelliJ analysis on all project files",
      SonarLintIcons.PROJECT);
    analyzeChangedFilesAction = new SonarAnalyzeChangedFilesAction("Analyze VCS Changed Files",
      "Run a SonarQube for IntelliJ analysis on VCS changed files",
      SonarLintIcons.SCM);
    filterAction = new FilterSecurityHotspotActionGroup("Filter Security Hotspots",
      "Filter Security Hotspots",
      AllIcons.General.Filter);
    includeResolvedHotspotsAction = new IncludeResolvedFindingsAction<>("Include Resolved Security Hotspots",
      "Include resolved Security Hotspots",
      SonarLintIcons.RESOLVED,
      LiveSecurityHotspot.class);
    includeResolvedIssuesAction = new IncludeResolvedFindingsAction<>("Include Resolved Issues",
      "Include resolved issues",
      SonarLintIcons.RESOLVED,
      LiveIssue.class);
    includeResolvedTaintVulnerabilitiesAction = new IncludeResolvedFindingsAction<>("Include Resolved Taint Vulnerabilities",
      "Include resolved taint vulnerabilities",
      SonarLintIcons.RESOLVED,
      LocalTaintVulnerability.class);
    analyzeCurrentFileAction = new SonarAnalyzeFilesAction("Analyze Current File",
      "Run SonarQube for IntelliJ analysis on the current file",
      SonarLintIcons.PLAY);
    restartSonarLintAction = new RestartBackendAction();
  }

  public static SonarLintActions getInstance() {
    return SonarLintUtils.getService(SonarLintActions.class);
  }

  public AnAction cancelAnalysis() {
    return cancelAction;
  }

  public AnAction clearReport() {
    return clearReportAction;
  }

  public AnAction clearIssues() {
    return clearIssuesAction;
  }

  public AnAction configure() {
    return configureAction;
  }

  public AnAction cleanConsole() {
    return cleanConsoleAction;
  }

  public AnAction analyzeChangedFiles() {
    return analyzeChangedFilesAction;
  }

  public AnAction analyzeAllFiles() {
    return analyzeAllFilesAction;
  }

  public FilterSecurityHotspotActionGroup filterSecurityHotspots() {
    return filterAction;
  }

  public IncludeResolvedFindingsAction<LiveSecurityHotspot> includeResolvedHotspotAction() {
    return includeResolvedHotspotsAction;
  }

  public IncludeResolvedFindingsAction<LiveIssue> includeResolvedIssuesAction() {
    return includeResolvedIssuesAction;
  }

  public IncludeResolvedFindingsAction<LocalTaintVulnerability> includeResolvedTaintVulnerabilitiesAction() {
    return includeResolvedTaintVulnerabilitiesAction;
  }

  public AnAction analyzeCurrentFileAction() {
    return analyzeCurrentFileAction;
  }

  public AnAction restartSonarLintAction() {
    return restartSonarLintAction;
  }

}
