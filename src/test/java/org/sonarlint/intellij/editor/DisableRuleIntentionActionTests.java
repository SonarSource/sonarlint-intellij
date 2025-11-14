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
package org.sonarlint.intellij.editor;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisableRuleIntentionActionTests extends AbstractSonarLintLightTests {
  private DisableRuleIntentionAction quickFix;

  @BeforeEach
  void prepare() {
    // Reset rule activations
    getGlobalSettings().setRules(Collections.emptyList());
    quickFix = new DisableRuleIntentionAction("rule");
  }

  @Test
  void text_should_mention_rule_key() {
    assertThat(quickFix.getText()).isEqualTo("SonarQube: Disable rule 'rule'");
  }

  @Test
  void check_getters() {
    assertThat(quickFix.getIcon(0)).isEqualTo(AllIcons.Actions.Cancel);
    assertThat(quickFix.getFamilyName()).isEqualTo("SonarQube disable rule");
    assertThat(quickFix.startInWriteAction()).isFalse();
  }

  @Test
  void should_be_available() {
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isTrue();
  }

  @Test
  void should_not_be_available_if_already_excluded() {
    getGlobalSettings().disableRule("rule");
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  void should_not_be_available_if_project_bound() {
    getProjectSettings().setBindingEnabled(true);
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  void should_disable_rule_in_settings_when_invoked() {
    getGlobalSettings().setAutoTrigger(true);
    var file = PsiFileFactory.getInstance(getProject())
      .createFileFromText("MyClass.java", Language.findLanguageByID("JAVA"), "class MyClass {}", true, false);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(),false);

    quickFix.invoke(getProject(), mock(Editor.class), file);

    assertThat(getGlobalSettings().isRuleExplicitlyDisabled("rule")).isTrue();
  }
}
