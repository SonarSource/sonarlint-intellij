/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DisableRuleQuickFixTest extends SonarTest {
  private SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
  private SonarLintProjectSettings projectSettings = new SonarLintProjectSettings();
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private DisableRuleQuickFix quickFix;

  @Before
  public void prepare() {
    super.register(app, SonarLintGlobalSettings.class, settings);
    super.register(SonarLintProjectSettings.class, projectSettings);
    super.register(SonarLintSubmitter.class, submitter);
    quickFix = new DisableRuleQuickFix("rule");
  }

  @Test
  public void text_should_mention_rule_key() {
    assertThat(quickFix.getText()).isEqualTo("Disable SonarLint rule 'rule'");
  }

  @Test
  public void check_getters() {
    assertThat(quickFix.getIcon(0)).isEqualTo(AllIcons.Actions.Cancel);
    assertThat(quickFix.getFamilyName()).isEqualTo("SonarLint disable rule");
    assertThat(quickFix.startInWriteAction()).isFalse();
  }

  @Test
  public void should_be_available() {
    assertThat(quickFix.isAvailable(project, mock(Editor.class), mock(PsiFile.class))).isTrue();
  }

  @Test
  public void should_not_be_available_if_already_excluded() {
    settings.setExcludedRules(Collections.singleton("rule"));
    assertThat(quickFix.isAvailable(project, mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  public void should_not_be_available_if_project_bound() {
    projectSettings.setBindingEnabled(true);
    assertThat(quickFix.isAvailable(project, mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  public void should_exclude() {
    quickFix.invoke(project, mock(Editor.class), mock(PsiFile.class));
    assertThat(settings.getExcludedRules()).containsExactly("rule");
    verify(submitter).submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
  }
}
