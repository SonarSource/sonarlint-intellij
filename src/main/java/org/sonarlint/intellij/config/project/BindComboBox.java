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
package org.sonarlint.intellij.config.project;

import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.util.TextSearchIndex;

public class BindComboBox {
  private List<RemoteModule> modules;
  private JComboBox<RemoteModule> comboBox;
  private TextSearchIndex<RemoteModule> searchIndex = new TextSearchIndex<>();
  private JTextField textField;

  public BindComboBox() {
    textField = new JTextField("", 9);
    textField.setBorder(null);
    textField.setFocusable(true);
    textField.addFocusListener(new FocusListener() {
      @Override public void focusGained(FocusEvent e) {
        textField.setText("");
        comboBox.setPopupVisible(true);
      }

      @Override public void focusLost(FocusEvent e) {
        //nothing to do
      }
    });
    textField.addKeyListener(new KeyAdapter() {
      @Override public void keyReleased(KeyEvent key) {
        search(textField.getText());
      }
    });

    modules = new LinkedList<>();

    ComboBoxModel<RemoteModule> model = new DefaultComboBoxModel<>();
    comboBox = new JComboBox<>(model);
    comboBox.setEditor(new AutoCompleteComboBoxEditor());
    comboBox.setRenderer(new ProjectComboBoxRenderer());
    comboBox.setEditable(false);
  }

  public JComponent get() {
    return comboBox;
  }

  public void addItem(RemoteModule mod) {
    searchIndex.index(mod, mod.getName());
    comboBox.addItem(mod);
    modules.add(mod);
  }

  public void setEnabled(boolean enable) {
    comboBox.setEnabled(enable);
  }

  public void removeAllItems() {
    modules.clear();
    searchIndex.clear();
    comboBox.removeAllItems();
  }

  private void search(String text) {
    comboBox.removeAllItems();
    for (RemoteModule mod : searchIndex.search(text)) {
      comboBox.addItem(mod);
    }
  }

  @CheckForNull
  public String getSelectedKey() {
    return null;
  }

  private class AutoCompleteComboBoxEditor implements ComboBoxEditor {
    @Override
    public Component getEditorComponent() {
      return textField;
    }

    @Override
    public void setItem(Object obj) {
      if (obj == null) {
        textField.setText("");
      } else {
        RemoteModule mod = (RemoteModule) obj;
        textField.setText(mod.getKey());
      }
    }

    @Override
    public Object getItem() {
      return textField.getText();
    }

    @Override
    public void selectAll() {
      textField.selectAll();
      textField.requestFocus();
    }

    @Override
    public void addActionListener(ActionListener l) {
      textField.addActionListener(l);
    }
    @Override
    public void removeActionListener(ActionListener l) {
      textField.removeActionListener(l);
    }
  }

  /**
   * Render RemoteModule in combo box
   */
  private static class ProjectComboBoxRenderer extends ColoredListCellRendererWrapper<RemoteModule> {
    @Override protected void doCustomize(JList list, RemoteModule value, int index, boolean selected, boolean hasFocus) {
      if (list.getModel().getSize() == 0) {
        append("asd");
        return;
      }
      append(value.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      append(" ");
      append(value.getKey(), SimpleTextAttributes.GRAY_ATTRIBUTES, false);
      setToolTipText("Bind to this SonarQube project");
    }
  }
}
