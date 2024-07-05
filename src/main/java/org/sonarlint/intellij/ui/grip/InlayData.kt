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
package org.sonarlint.intellij.ui.grip

import java.time.Instant
import java.util.UUID
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestionReviewStatus

data class InlayData(
    val inlaySnippets: MutableList<InlaySnippetData>,
    var status: AiFindingState,
    val generatedDate: Instant,
    val correlationId: UUID?,
    var feedbackGiven: Boolean = false,
    val ruleMessage: String,
) {
    fun getStatus(): SuggestionReviewStatus? {
        return when (status) {
            AiFindingState.ACCEPTED -> SuggestionReviewStatus.ACCEPTED
            AiFindingState.DECLINED -> SuggestionReviewStatus.DECLINED
            AiFindingState.PARTIAL -> SuggestionReviewStatus.PARTIALLY_ACCEPTED
            else -> null
        }
    }
}

data class InlaySnippetData(
    var inlayPanel: InlayQuickFixPanel,
    var status: AiFindingState,
    val index: Int,
    val total: Int?,
)
