package org.sonarlint.intellij.ui.codefix

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

class SonarQubeDiffView(project: Project) : DiffRequestProcessor(project) {

    override fun updateRequest(force: Boolean, scrollToChangePolicy: DiffUserDataKeysEx.ScrollToPolicy?) {
        super.updateRequest(force)
    }

    override fun buildToolbar(viewerActions: MutableList<out AnAction>?) {
        // Nothing
    }

    fun applyRequest(
        request: DiffRequest,
    ) {
        super.applyRequest(request, false, DiffUserDataKeysEx.ScrollToPolicy.FIRST_CHANGE)
    }

}
