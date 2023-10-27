/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.editor

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.SonarLintTextAttributes
import org.sonarlint.intellij.finding.persistence.FindingsCache
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarsource.sonarlint.core.commons.ImpactSeverity
import org.sonarsource.sonarlint.core.commons.IssueSeverity

internal class SonarExternalAnnotatorTests : AbstractSonarLintLightTests() {
    private val psiFile: PsiFile = Mockito.mock(PsiFile::class.java)
    private val virtualFile: VirtualFile = Mockito.mock(VirtualFile::class.java)
    private val store: FindingsCache = Mockito.mock(FindingsCache::class.java)
    private val psiFileRange = TextRange(0, 100)

    @BeforeEach
    fun set() {
        replaceProjectService(FindingsCache::class.java, store)
        Mockito.`when`(psiFile.textRange).thenReturn(psiFileRange)
        Mockito.`when`(psiFile.virtualFile).thenReturn(virtualFile)
        Mockito.`when`(psiFile.fileType).thenReturn(JavaFileType.INSTANCE)
        Mockito.`when`(psiFile.project).thenReturn(project)
        Settings.getGlobalSettings().isFocusOnNewCode = false
    }

    @Test
    fun testSeverityMapping() {
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, null, false)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.LOW)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.LOW)

        Settings.getGlobalSettings().isFocusOnNewCode = true
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")

        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)

        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MAJOR, true)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.MINOR, true)).isEqualTo(SonarLintTextAttributes.LOW)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.BLOCKER, true)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.CRITICAL, true)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, IssueSeverity.INFO, true)).isEqualTo(SonarLintTextAttributes.LOW)
    }

    @Test
    fun testImpactMapping() {
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, null, null, false)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.LOW)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.LOW)

        Settings.getGlobalSettings().isFocusOnNewCode = true
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")

        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE)

        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, true)).isEqualTo(SonarLintTextAttributes.MEDIUM)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.MINOR, true)).isEqualTo(SonarLintTextAttributes.LOW)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.BLOCKER, true)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.HIGH, IssueSeverity.CRITICAL, true)).isEqualTo(SonarLintTextAttributes.HIGH)
        assertThat(SonarExternalAnnotator.getTextAttrsKey(project, ImpactSeverity.LOW, IssueSeverity.INFO, true)).isEqualTo(SonarLintTextAttributes.LOW)
    }
}
