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
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DisableRuleIntentionActionTest extends AbstractSonarLintLightTests {
  private DisableRuleIntentionAction quickFix;

  @Before
  public void prepare() {
    // Reset rule activations
    getGlobalSettings().setRules(Collections.emptyList());
    quickFix = new DisableRuleIntentionAction("rule");
  }

  @Test
  public void text_should_mention_rule_key() {
    assertThat(quickFix.getText()).isEqualTo("SonarLint: Disable rule 'rule'");
  }

  @Test
  public void check_getters() {
    assertThat(quickFix.getIcon(0)).isEqualTo(AllIcons.Actions.Cancel);
    assertThat(quickFix.getFamilyName()).isEqualTo("SonarLint disable rule");
    assertThat(quickFix.startInWriteAction()).isFalse();
  }

  @Test
  public void should_be_available() {
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isTrue();
  }

  @Test
  public void should_not_be_available_if_already_excluded() {
    getGlobalSettings().disableRule("rule");
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  public void should_not_be_available_if_project_bound() {
    getProjectSettings().setBindingEnabled(true);
    assertThat(quickFix.isAvailable(getProject(), mock(Editor.class), mock(PsiFile.class))).isFalse();
  }

  @Test
  public void should_exclude() {
    SonarLintConsole console = SonarLintUtils.getService(getProject(), SonarLintConsole.class);
    getGlobalSettings().setAutoTrigger(true);

    PsiFile file = PsiFileFactory.getInstance(getProject())
      .createFileFromText("MyClass.java", Language.findLanguageByID("JAVA"), "public class MyClass {}", true, false);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(),false);
    quickFix.invoke(getProject(), mock(Editor.class), file);

    assertThat(getGlobalSettings().isRuleExplicitlyDisabled("rule")).isTrue();
    assertThat(((SonarLintConsoleTestImpl) console).getLastMessage()).isEqualTo("[Binding update] 0 file(s) submitted");
  }
}
