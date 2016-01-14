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
package org.sonarlint.intellij.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class NoSonarIntentionActionTest extends LightPlatformCodeInsightFixtureTestCase {
  private PsiFile testFile;
  private SonarLintAnalyzer analyzer;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testFile = myFixture.addFileToProject("testFile.java", "dummy");
    analyzer = SonarLintTestUtils.mockInContainer(SonarLintAnalyzer.class, getProject());
  }

  @Test
  public void testNoRange() {
    myFixture.openFileInEditor(testFile.getVirtualFile());

    NoSonarIntentionAction noSonarAction = new NoSonarIntentionAction(null);
    assertThat(noSonarAction.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile())).isFalse();
  }

  @Test
  public void testInvokeNoRange() {
    // this should never happen because, but just in case
    NoSonarIntentionAction noSonarAction = new NoSonarIntentionAction(null);
    noSonarAction.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());

    verifyZeroInteractions(analyzer);
  }

  @Test
  public void testIsAvailable() {
    myFixture.openFileInEditor(testFile.getVirtualFile());
    DocumentationManager.getInstance(getProject());
    Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(testFile);
    RangeMarker range = doc.createRangeMarker(0, 1);
    NoSonarIntentionAction noSonarAction = new NoSonarIntentionAction(range);
    assertThat(noSonarAction.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile())).isTrue();
    noSonarAction.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());

    assertThat(doc.getText()).isEqualTo("dummy // NOSONAR");
    verify(analyzer).submitAsync(myModule, Collections.singleton(testFile.getVirtualFile()));
  }
}
