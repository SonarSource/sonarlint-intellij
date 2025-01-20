/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.telemetry

import com.intellij.ide.BrowserUtil
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Community.COMMUNITY_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.AI_FIX_SUGGESTIONS_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.BASE_DOCS_URL
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK

enum class LinkTelemetry(
    private val linkId: String,
    val url: String
) {

    SONARCLOUD_FREE_SIGNUP_PAGE("sonarqubeCloudFreeSignUp", SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_SIGNUP_LINK),
    CONNECTED_MODE_DOCS("connectedModeDocs", SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK),
    SONARQUBE_EDITIONS_DOWNLOADS("sonarQubeEditionsDownloads", SonarLintDocumentation.Marketing.SONARQUBE_EDITIONS_DOWNLOADS_LINK),
    RULE_SELECTION_PAGE("rulesSelectionDocs", RULE_SECTION_LINK),
    AI_FIX_SUGGESTIONS_PAGE("aiFixSuggestionsDocs", AI_FIX_SUGGESTIONS_LINK),
    COMMUNITY_PAGE("communityReportPage", COMMUNITY_LINK),
    BASE_DOCS_PAGE("baseDocs", BASE_DOCS_URL);

    fun browseWithTelemetry() {
        SonarLintUtils.getService(SonarLintTelemetry::class.java).helpAndFeedbackLinkClicked(linkId)
        BrowserUtil.browse(url)
    }

}
