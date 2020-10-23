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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class OpenHotspotDialog extends DialogWrapper {

  private JBTextField textField;

  public OpenHotspotDialog() {
    super(false);
    init();
    setTitle("Open Hotspot");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel dialogPanel = new JPanel(new BorderLayout());

    textField = new JBTextField();
    textField.setToolTipText("Hotspot ID");
    textField.getEmptyText().setText("Hotspot ID");
    dialogPanel.add(textField, BorderLayout.CENTER);

    return dialogPanel;
  }

  public String getHotspotId() {
    return textField.getText();
  }

}
