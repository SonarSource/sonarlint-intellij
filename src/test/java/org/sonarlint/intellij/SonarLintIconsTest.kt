/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.icons.SonarLintIcons.getIconForTypeAndSeverity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.hotspotTypeWithProbability
import org.sonarlint.intellij.ui.icons.SonarLintIcons.impact
import org.sonarlint.intellij.ui.icons.SonarLintIcons.loadingCodeFixIcon
import org.sonarlint.intellij.ui.icons.SonarLintIcons.riskSeverity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.severity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.toDisabled
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

class SonarLintIconsTest {
    @Test
    fun `test severities`() {
        for (value in IssueSeverity.values()) {
            assertThat(severity(value)).isNotNull
        }
    }

    @Test
    fun `test impacts`() {
        for (value in ImpactSeverity.values()) {
            assertThat(impact(value)).isNotNull
        }
    }

    @Test
    fun `test risk severities`() {
        for (value in DependencyRiskDto.Severity.values()) {
            assertThat(riskSeverity (value)).isNotNull
        }
    }

    @Test
    fun `test types`() {
        for (type in RuleType.values()) {
            for (severity in IssueSeverity.values()) {
                if (type != RuleType.SECURITY_HOTSPOT) {
                    assertThat(getIconForTypeAndSeverity(type, severity)).isNotNull
                }
            }
        }
        for (value in VulnerabilityProbability.values()) {
            assertThat(hotspotTypeWithProbability(value)).isNotNull
        }
    }

    @Test
    fun `test loading code fix`() {
        assertThat(loadingCodeFixIcon()).isNotNull
    }

    @Test
    fun `test icons`() {
        assertThat(SonarLintIcons.ICON_SONARQUBE_SERVER).isNotNull
        assertThat(SonarLintIcons.ICON_SONARQUBE_CLOUD).isNotNull
        assertThat(SonarLintIcons.ICON_SONARQUBE_SERVER_16).isNotNull
        assertThat(SonarLintIcons.ICON_SONARQUBE_CLOUD_16).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ_13PX).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ_RED_13PX).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ_GREEN_13PX).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ_ORANGE_13PX).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ).isNotNull
        assertThat(SonarLintIcons.SONARQUBE_FOR_INTELLIJ_32PX).isNotNull
        assertThat(SonarLintIcons.PLAY).isNotNull
        assertThat(SonarLintIcons.CLEAN).isNotNull
        assertThat(SonarLintIcons.TOOLS).isNotNull
        assertThat(SonarLintIcons.SUSPEND).isNotNull
        assertThat(SonarLintIcons.INFO).isNotNull
        assertThat(SonarLintIcons.WARN).isNotNull
        assertThat(SonarLintIcons.SCM).isNotNull
        assertThat(SonarLintIcons.PROJECT).isNotNull
        assertThat(SonarLintIcons.NOT_CONNECTED).isNotNull
        assertThat(SonarLintIcons.CONNECTED).isNotNull
        assertThat(SonarLintIcons.CONNECTION_ERROR).isNotNull
        assertThat(SonarLintIcons.RESOLVED).isNotNull
        assertThat(SonarLintIcons.CODEFIX_PRESENTATION).isNotNull
        assertThat(SonarLintIcons.SPARKLE_GUTTER_ICON).isNotNull
        assertThat(SonarLintIcons.WALKTHROUGH_LEARN_AS_YOU_CODE).isNotNull
        assertThat(SonarLintIcons.WALKTHROUGH_WELCOME).isNotNull
        assertThat(SonarLintIcons.WALKTHROUGH_CONNECT_WITH_YOUR_TEAM).isNotNull
        assertThat(SonarLintIcons.WALKTHROUGH_REACH_OUT_TO_US).isNotNull
        assertThat(SonarLintIcons.WALKTHROUGH_TOOLWINDOW_ICON).isNotNull
        assertThat(SonarLintIcons.STATUS_FALSE_POSITIVE).isNotNull
        assertThat(SonarLintIcons.STATUS_LINK_OFF).isNotNull
        assertThat(SonarLintIcons.STATUS_ACCEPTED).isNotNull
    }

    @Test
    fun `test disabled`() {
        assertThat(toDisabled(SonarLintIcons.WARN)).isNotNull
    }
}
