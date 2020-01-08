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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ErrorPainter extends AbstractPainter {
  private Set<JComponent> componentsWithErrors = new HashSet<>();
  private JPanel panel;

  @Override
  public void executePaint(Component component, Graphics2D g) {
    for (JComponent comp : componentsWithErrors) {
      int w = comp.getWidth();
      Point p = SwingUtilities.convertPoint(comp, w, 0, component);
      AllIcons.General.Error.paintIcon(component, g, p.x - 8, p.y - 8);
    }
  }

  public void installOn(JPanel component, Disposable disposable) {
    this.panel = component;
    UIUtil.invokeLaterIfNeeded(() -> IdeGlassPaneUtil.installPainter(component, this, disposable));
  }

  public void addComponentWithErrors(JComponent component) {
    if (componentsWithErrors.add(component)) {
      panel.getRootPane().getGlassPane().repaint();
    }
  }

  public void setValid(JComponent component, boolean valid) {
    if (valid) {
      removeComponentWithErrors(component);
    } else {
      addComponentWithErrors(component);
    }
  }

  public void removeComponentWithErrors(JComponent component) {
    if (componentsWithErrors.remove(component)) {
      panel.getRootPane().getGlassPane().repaint();
    }
  }

  @Override
  public boolean needsRepaint() {
    return true;
  }
}
