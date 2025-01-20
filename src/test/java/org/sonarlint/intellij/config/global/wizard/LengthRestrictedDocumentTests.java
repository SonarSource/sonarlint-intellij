/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.config.global.wizard;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LengthRestrictedDocumentTests {
  private LengthRestrictedDocument doc;

  @BeforeEach
  void setUp() {
    doc = new LengthRestrictedDocument(3);
  }

  @Test
  void testNullStr() throws BadLocationException {
    doc.insertString(0, null, new SimpleAttributeSet());
    assertThat(doc.getLength()).isZero();
  }

  @Test
  void testLimit() throws BadLocationException {
    doc.insertString(doc.getLength(), "txt", new SimpleAttributeSet());
    assertThat(doc.getText(0, doc.getLength())).isEqualTo("txt");

    doc.insertString(doc.getLength(), "more", new SimpleAttributeSet());
    assertThat(doc.getText(0, doc.getLength())).isEqualTo("txt");
  }
}
