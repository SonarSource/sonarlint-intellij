/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Objects;
import javax.swing.Icon;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.sonarlint.intellij.common.util.SonarLintUtils.pluralize;

public class FileNode extends AbstractNode {
  private final VirtualFile file;
  private final boolean isSecurityHotspot;

  public FileNode(VirtualFile file, boolean isSecurityHotspot) {
    this.file = file;
    this.isSecurityHotspot = isSecurityHotspot;
  }

  public VirtualFile file() {
    return file;
  }

  @Override
  public int getFindingCount() {
    return super.getChildCount();
  }

  public Icon getIcon() {
    return file.getFileType().getIcon();
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    renderer.setIcon(getIcon());
    renderer.setIconToolTip(file.getFileType().getDisplayName() + " file");
    renderer.append(file.getName());
    renderer.append(spaceAndThinSpace() + "(" + getFindingCount() + pluralize(isSecurityHotspot ? " Security Hotspot" : " issue", getFindingCount()) + ")",
      SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    renderer.setToolTipText("Double click to list file " + pluralize(isSecurityHotspot ? " Security Hotspot" : " issue", getFindingCount()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var fileNode = (FileNode) o;
    return Objects.equals(file, fileNode.file);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file);
  }

  @Override
  public String toString() {
    return file.getName();
  }
}
