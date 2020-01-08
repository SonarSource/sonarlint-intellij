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

import com.intellij.openapi.editor.Editor;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLinkHandlerTest extends SonarTest {
  private static final String RULE_KEY = "setRuleKey";
  private SonarLinkHandler handler = new SonarLinkHandler();
  private Editor editor = mock(Editor.class);
  private SonarLintFacade sonarlint = mock(SonarLintFacade.class);

  @Before
  public void prepare() throws InvalidBindingException {
    ProjectBindingManager projectBindingManager = register(ProjectBindingManager.class);

    when(projectBindingManager.getFacade()).thenReturn(sonarlint);
    when(editor.getProject()).thenReturn(project);
  }

  @Test
  public void testDescription() {
    when(sonarlint.getDescription(RULE_KEY)).thenReturn("description");
    when(sonarlint.getRuleName(RULE_KEY)).thenReturn("name");
    String desc = handler.getDescription(RULE_KEY, editor);
    assertThat(desc).contains("description");
    assertThat(desc).contains("name");
    assertThat(desc).contains(RULE_KEY);
    verify(sonarlint).getDescription(RULE_KEY);
    verify(sonarlint).getRuleName(RULE_KEY);
  }

  @Test
  public void testDescriptionWithoutProject() {
    when(editor.getProject()).thenReturn(null);

    assertThat(handler.getDescription(RULE_KEY, editor)).isNull();
    verifyZeroInteractions(sonarlint);
  }

  @Test
  public void testRuleDoesntExist() {
    when(sonarlint.getDescription(RULE_KEY)).thenReturn(null);
    when(sonarlint.getRuleName(RULE_KEY)).thenReturn(null);

    String desc = handler.getDescription(RULE_KEY, editor);
    assertThat(desc).contains(RULE_KEY);
    verify(sonarlint).getDescription(RULE_KEY);
    verify(sonarlint).getRuleName(RULE_KEY);
  }

  @Test
  public void testRemoveEmptyLines() {
    when(sonarlint.getDescription(RULE_KEY)).thenReturn("text1\n\n\ntext2");
    when(sonarlint.getRuleName(RULE_KEY)).thenReturn("name");

    String desc = handler.getDescription(RULE_KEY, editor);
    assertThat(desc).contains("text1\ntext2");
    verify(sonarlint).getDescription(RULE_KEY);
    verify(sonarlint).getRuleName(RULE_KEY);
  }
  @Test
  public void testEscapeHtmlInName() {
    when(sonarlint.getDescription(RULE_KEY)).thenReturn("description");
    when(sonarlint.getRuleName(RULE_KEY)).thenReturn("name with <html> tag");

    String desc = handler.getDescription(RULE_KEY, editor);
    assertThat(desc).contains("&lt;html&gt;");
    assertThat(desc).contains("description");
    verify(sonarlint).getDescription(RULE_KEY);
    verify(sonarlint).getRuleName(RULE_KEY);
  }

  @Test
  public void testHandler() {
    assertThat(handler.handleLink(RULE_KEY, editor)).isFalse();
  }
}
