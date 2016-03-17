/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import org.sonarlint.intellij.analysis.SonarLintFacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLinkHandlerTest extends SonarTest {
  private static final String RULE_KEY = "ruleKey";
  private SonarLinkHandler handler;
  private Editor editor;
  private SonarLintFacade sonarlint;

  @Before
  public void setUp() {
    super.setUp();
    sonarlint = mock(SonarLintFacade.class);
    editor = mock(Editor.class);
    handler = new SonarLinkHandler();

    when(editor.getProject()).thenReturn(project);
    when(sonarlint.getDescription(RULE_KEY)).thenReturn("description");
    when(sonarlint.getRuleName(RULE_KEY)).thenReturn("name");
    register(SonarLintFacade.class, sonarlint);
  }

  @Test
  public void testDescription() {
    String desc = handler.getDescription(RULE_KEY, editor);
    assertThat(desc).contains("description");
    assertThat(desc).contains("name");
    assertThat(desc).contains(RULE_KEY);
    verify(sonarlint).getDescription(RULE_KEY);
    verify(sonarlint).getRuleName(RULE_KEY);
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
  public void testHandler() {
    assertThat(handler.handleLink(RULE_KEY, editor)).isFalse();
  }
}
