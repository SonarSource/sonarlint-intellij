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

import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.SearchTextField;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class FilterComponent extends JPanel {
  private final SearchTextField myFilter;
  private final Alarm myUpdateAlarm = new Alarm();
  private boolean myOnTheFly;

  public FilterComponent() {
    this(true);
  }

  public FilterComponent(boolean onTheFlyUpdate) {
    super(new BorderLayout());
    myOnTheFly = onTheFlyUpdate;
    myFilter = new SearchTextField(false) {
      @Override
      protected Runnable createItemChosenCallback(JList list) {
        final Runnable callback = super.createItemChosenCallback(list);
        return new Runnable() {
          @Override
          public void run() {
            callback.run();
            filter();
          }
        };
      }

      @Override
      protected Component getPopupLocationComponent() {
        return FilterComponent.this.getPopupLocationComponent();
      }
    };
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          myFilter.addCurrentTextToHistory();
          filter();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          onEscape(e);
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      public void changedUpdate(DocumentEvent e) {
        onChange();
      }
    });

    add(myFilter, BorderLayout.CENTER);
  }

  protected JComponent getPopupLocationComponent() {
    return myFilter;
  }

  public JTextField getTextEditor() {
    return myFilter.getTextEditor();
  }

  private void onChange() {
    if (myOnTheFly) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(new Runnable() {
        public void run() {
          onlineFilter();
        }
      }, 100, ModalityState.stateForComponent(myFilter));
    }
  }

  protected void onEscape(KeyEvent e) {
    // Implement this method
  }

  public String getFilter() {
    return myFilter.getText();
  }

  public void setSelectedItem(final String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void setFilter(final String filter) {
    myFilter.setText(filter);
  }

  public void selectText() {
    myFilter.selectText();
  }

  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public void filter() {
    // To be implemented
  }

  protected void onlineFilter() {
    filter();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }

}
