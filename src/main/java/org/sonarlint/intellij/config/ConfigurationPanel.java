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
package org.sonarlint.intellij.config;

import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.UIUtil;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.text.DefaultCaret;

public interface ConfigurationPanel<T> {
  JComponent getComponent();

  boolean isModified(T settings);

  void save(T settings);

  void load(T settings);

  default void initHtmlPane(JEditorPane editorPane) {
    editorPane.setContentType(UIUtil.HTML_MIME);
    if (editorPane.getCaret() == null) {
      editorPane.setCaret(new DefaultCaret());
    }
    ((DefaultCaret) editorPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    editorPane.setEditorKit(HTMLEditorKitBuilder.simple());
    editorPane.setFocusable(false);
    editorPane.setEditable(false);
    editorPane.setOpaque(false);
  }
}
