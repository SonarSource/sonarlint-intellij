/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.editor.Editor;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLinkHandlerTest extends AbstractSonarLintLightTests {
  private static final String RULE_KEY = "setRuleKey";
  private SonarLinkHandler handler = new SonarLinkHandler();
  private SonarLintFacade sonarlintFacade = mock(SonarLintFacade.class);

  @Before
  public void prepare() throws InvalidBindingException {
    var projectBindingManager = mock(ProjectBindingManager.class);
    replaceProjectService(ProjectBindingManager.class, projectBindingManager);

    when(projectBindingManager.getFacade(any())).thenReturn(sonarlintFacade);

    myFixture.configureByFile("file.py");
  }

  @Test
  public void testDescription() {
    when(sonarlintFacade.getDescription(RULE_KEY)).thenReturn("description");
    when(sonarlintFacade.getRuleName(RULE_KEY)).thenReturn("name");
    var desc = handler.getDescription(RULE_KEY, myFixture.getEditor());
    assertThat(desc)
      .contains("description")
      .contains("name")
      .contains(RULE_KEY);
    verify(sonarlintFacade).getDescription(RULE_KEY);
    verify(sonarlintFacade).getRuleName(RULE_KEY);
  }

  @Test
  public void testDescriptionWithoutProject() {
    var editor = mock(Editor.class);
    when(editor.getProject()).thenReturn(null);

    assertThat(handler.getDescription(RULE_KEY, editor)).isNull();
    verifyZeroInteractions(sonarlintFacade);
  }

  @Test
  public void testRuleDoesntExist() {
    when(sonarlintFacade.getDescription(RULE_KEY)).thenReturn(null);
    when(sonarlintFacade.getRuleName(RULE_KEY)).thenReturn(null);

    var desc = handler.getDescription(RULE_KEY, myFixture.getEditor());
    assertThat(desc).contains(RULE_KEY);
    verify(sonarlintFacade).getDescription(RULE_KEY);
    verify(sonarlintFacade).getRuleName(RULE_KEY);
  }

  @Test
  public void testRemoveBlankLines() {
    when(sonarlintFacade.getDescription(RULE_KEY)).thenReturn("text1\n   \t\n \r\ntext2");
    when(sonarlintFacade.getRuleName(RULE_KEY)).thenReturn("name");

    var desc = handler.getDescription(RULE_KEY, myFixture.getEditor());
    assertThat(desc).contains("text1\ntext2");
    verify(sonarlintFacade).getDescription(RULE_KEY);
    verify(sonarlintFacade).getRuleName(RULE_KEY);
  }
  @Test
  public void testEscapeHtmlInName() {
    when(sonarlintFacade.getDescription(RULE_KEY)).thenReturn("description");
    when(sonarlintFacade.getRuleName(RULE_KEY)).thenReturn("name with <html> tag");

    var desc = handler.getDescription(RULE_KEY, myFixture.getEditor());
    assertThat(desc)
      .contains("&lt;html&gt;")
      .contains("description");
    verify(sonarlintFacade).getDescription(RULE_KEY);
    verify(sonarlintFacade).getRuleName(RULE_KEY);
  }

  @Test
  public void testHandler() {
    assertThat(handler.handleLink(RULE_KEY, myFixture.getEditor())).isFalse();
  }
}
