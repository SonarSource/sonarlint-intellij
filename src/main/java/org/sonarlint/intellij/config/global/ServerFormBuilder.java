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
package org.sonarlint.intellij.config.global;

import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import static java.awt.GridBagConstraints.EAST;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;

/**
 * Inspired by {@link FormBuilder}
 */
public class ServerFormBuilder {
  private JPanel panel;
  private int rowGap;
  private int lineCount;
  private int columnGap;
  private int leftIndent;

  public ServerFormBuilder() {
    panel = new JPanel(new GridBagLayout());
    lineCount = 0;
    rowGap = UIUtil.DEFAULT_VGAP;
    columnGap = UIUtil.DEFAULT_HGAP;
  }

  public ServerFormBuilder setRowGap(int rowGap) {
    this.rowGap = rowGap;
    return this;
  }

  public ServerFormBuilder addSeparator(int size) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridwidth = 3;
    c.gridx = 0;
    c.gridy = lineCount;
    c.weightx = 0;
    c.weighty = 0;
    c.fill = HORIZONTAL;
    c.anchor = EAST;
    c.insets = new Insets(size, leftIndent, 0, columnGap);

    panel.add(new JSeparator(), c);
    lineCount++;
    return this;
  }

  public ServerFormBuilder setColumnGap(int columnGap) {
    this.columnGap = columnGap;
    return this;
  }

  public ServerFormBuilder setLeftIndent(int leftIndent) {
    this.leftIndent = leftIndent;
    return this;
  }

  public ServerFormBuilder addLabeledComponentWithButton(JComponent label, JComponent component, JButton button) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridwidth = 1;
    c.gridx = 0;
    c.gridy = lineCount;
    c.weightx = 0;
    c.weighty = 0;
    c.fill = NONE;
    c.anchor = EAST;
    c.insets = new Insets(rowGap, leftIndent, 0, columnGap);

    panel.add(label, c);

    c.gridx = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.fill = HORIZONTAL;
    c.anchor = WEST;
    c.insets = new Insets(rowGap, columnGap, 0, 0);

    panel.add(component, c);

    c.gridx = 2;
    c.weightx = 0;
    c.weighty = 0;
    c.fill = NONE;
    c.anchor = EAST;
    c.insets = new Insets(rowGap, columnGap, 0, 0);

    panel.add(button, c);

    lineCount++;
    return this;
  }

  public ServerFormBuilder addLabeledComponent(JComponent label, JComponent component, boolean fill) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridwidth = 1;
    c.gridx = 0;
    c.gridy = lineCount;
    c.weightx = 0;
    c.weighty = 0;
    c.fill = NONE;
    c.anchor = EAST;
    c.insets = new Insets(rowGap, leftIndent, 0, columnGap);

    panel.add(label, c);

    c.gridwidth = 2;
    c.gridx = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.fill = fill ? HORIZONTAL : NONE;
    c.anchor = WEST;
    c.insets = new Insets(rowGap, columnGap, 0, 0);

    panel.add(component, c);

    lineCount++;
    return this;
  }

  public JPanel getPanel() {
    return panel;
  }
}
