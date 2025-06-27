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
import org.sonarlint.intellij.common.util.UrlUtils
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Community.COMMUNITY_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.AI_FIX_SUGGESTIONS_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.BASE_DOCS_URL
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.INVESTIGATING_ISSUES_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.OPEN_IN_IDE_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.TROUBLESHOOTING_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.USING_RULES_LINK
import org.sonarlint.intellij.promotion.Promotion

enum class LinkTelemetry(
    private val linkId: String,
    val url: String
) {

    SONARCLOUD_FREE_SIGNUP_PAGE("sonarqubeCloudFreeSignUp", SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_SIGNUP_LINK),
    CONNECTED_MODE_DOCS("connectedModeDocs", SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK),
    SONARQUBE_EDITIONS_DOWNLOADS("sonarQubeEditionsDownloads", SonarLintDocumentation.Marketing.SONARQUBE_EDITIONS_DOWNLOADS_LINK),
    RULE_SELECTION_PAGE("rulesSelectionDocs", RULE_SECTION_LINK),
    USING_RULES_PAGE("usingRulesDocs", USING_RULES_LINK),
    INVESTIGATING_ISSUES_PAGE("investigatingIssuesDocs", INVESTIGATING_ISSUES_LINK),
    OPEN_IN_IDE_PAGE("openInIdeDocs", OPEN_IN_IDE_LINK),
    TROUBLESHOOTING_PAGE("troubleshootingPage", TROUBLESHOOTING_LINK),
    AI_FIX_SUGGESTIONS_PAGE("aiFixSuggestionsDocs", AI_FIX_SUGGESTIONS_LINK),
    COMMUNITY_PAGE("communityReportPage", COMMUNITY_LINK),
    BASE_DOCS_WALKTHROUGH("baseDocs", BASE_DOCS_URL),
    BASE_DOCS_HELP("docs", BASE_DOCS_URL),
    COMMUNITY_HELP("gethelp", COMMUNITY_LINK),
    SUGGEST_FEATURE_HELP("suggestfeature", SonarLintDocumentation.Marketing.SONARQUBE_FOR_IDE_ROADMAP_LINK);

    fun browseWithTelemetry() {
        browseWithTelemetry(null)
    }

    fun browseWithTelemetry(promotion: Promotion?) {
        SonarLintUtils.getService(SonarLintTelemetry::class.java).helpAndFeedbackLinkClicked(linkId)

        BrowserUtil.browse(withParameters(promotion))
    }

    private fun withParameters(promotion: Promotion?): String {
        var promotionParams = emptyMap<String, String>()
        if (promotion != null) {
            promotionParams = promotion.trackingParams
        }
        return UrlUtils.addParameters(url, promotionParams)
    }

}
