/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.telemetry;

import java.util.Set;
import javax.annotation.Nullable;

import org.sonarsource.sonarlint.core.commons.Language;

public interface SonarLintTelemetry {
  void optOut(boolean optOut);

  boolean enabled();

  boolean canBeEnabled();

  void analysisDoneOnMultipleFiles();

  void analysisDoneOnSingleLanguage(@Nullable Language language, int time);

  void init();

  void devNotificationsReceived(String eventType);

  void devNotificationsClicked(String eventType);

  void showHotspotRequestReceived();

  void taintVulnerabilitiesInvestigatedRemotely();

  void taintVulnerabilitiesInvestigatedLocally();

  void addReportedRules(Set<String> reportedRules);

  void addQuickFixAppliedForRule(String ruleKey);
}
