/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.associate;

import org.sonar.ide.intellij.wsclient.ISonarRemoteProject;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class AssociateComboBoxEditor implements ComboBoxEditor, FocusListener {
  protected JTextField editor;
  private ISonarRemoteProject oldValue;

  public AssociateComboBoxEditor() {
    editor = createEditorComponent();
  }

  public Component getEditorComponent() {
    return editor;
  }

  /**
   * Creates the internal editor component. Override this to provide
   * a custom implementation.
   *
   * @return a new editor component
   * @since 1.6
   */
  protected JTextField createEditorComponent() {
    JTextField editor = new BorderlessTextField("", 9);
    editor.setBorder(null);
    return editor;
  }

  /**
   * Sets the item that should be edited.
   *
   * @param anObject the displayed value of the editor
   */
  public void setItem(Object anObject) {
    if (anObject instanceof ISonarRemoteProject) {
      ISonarRemoteProject module = (ISonarRemoteProject) anObject;
      editor.setText(module.getName() + " (" + module.getServer().getId() + ")");

      oldValue = module;
    } else {
      editor.setText("");
    }
  }

  public Object getItem() {
    Object newValue = editor.getText();
    // TODO
    return oldValue;
  }

  public void selectAll() {
    editor.selectAll();
    editor.requestFocus();
  }

  public void focusGained(FocusEvent e) {
    // Nothing to do
  }

  public void focusLost(FocusEvent e) {
    // Nothing to do
  }

  public void addActionListener(ActionListener l) {
    editor.addActionListener(l);
  }

  public void removeActionListener(ActionListener l) {
    editor.removeActionListener(l);
  }

  static class BorderlessTextField extends JTextField {
    public BorderlessTextField(String value, int n) {
      super(value, n);
    }

    // workaround for 4530952
    public void setText(String s) {
      if (getText().equals(s)) {
        return;
      }
      super.setText(s);
    }

    public void setBorder(Border b) {
      if (!(b instanceof UIResource)) {
        super.setBorder(b);
      }
    }
  }

  /**
   * A subclass of AssociateComboBoxEditor that implements UIResource.
   * AssociateComboBoxEditor doesn't implement UIResource
   * directly so that applications can safely override the
   * cellRenderer property with BasicListCellRenderer subclasses.
   * <p/>
   * <strong>Warning:</strong>
   * Serialized objects of this class will not be compatible with
   * future Swing releases. The current serialization support is
   * appropriate for short term storage or RMI between applications running
   * the same version of Swing.  As of 1.4, support for long term storage
   * of all JavaBeans<sup><font size="-2">TM</font></sup>
   * has been added to the <code>java.beans</code> package.
   * Please see {@link java.beans.XMLEncoder}.
   */
  public static class UIResource extends AssociateComboBoxEditor
      implements javax.swing.plaf.UIResource {
  }
}
