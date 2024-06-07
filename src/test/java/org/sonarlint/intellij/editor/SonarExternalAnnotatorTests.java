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
package org.sonarlint.intellij.editor;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarExternalAnnotatorTests extends AbstractSonarLintLightTests {
  private final PsiFile psiFile = mock(PsiFile.class);
  private final VirtualFile virtualFile = mock(VirtualFile.class);
  private final TextRange psiFileRange = new TextRange(0, 100);

  @BeforeEach
  void set() {
    when(psiFile.getTextRange()).thenReturn(psiFileRange);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
    when(psiFile.getFileType()).thenReturn(JavaFileType.INSTANCE);
    when(psiFile.getProject()).thenReturn(getProject());
    Settings.getGlobalSettings().setFocusOnNewCode(false);
  }

  @Test
  void testSeverityMapping() {
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, null, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.LOW);

    Settings.getGlobalSettings().setFocusOnNewCode(true);
    connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey");

    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);

    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MAJOR, true)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.MINOR, true)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.BLOCKER, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.CRITICAL, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, IssueSeverity.INFO, true)).isEqualTo(SonarLintTextAttributes.LOW);
  }

  @Test
  void testImpactMapping() {
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), null, null, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.LOW);

    Settings.getGlobalSettings().setFocusOnNewCode(true);
    connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey");

    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.MINOR, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.INFO, false)).isEqualTo(SonarLintTextAttributes.OLD_CODE);

    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, true)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.MINOR, true)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.BLOCKER, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.HIGH, IssueSeverity.CRITICAL, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(getProject(), ImpactSeverity.LOW, IssueSeverity.INFO, true)).isEqualTo(SonarLintTextAttributes.LOW);
  }
}
