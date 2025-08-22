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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.runReadActionSafely;

public abstract class AbstractNode extends DefaultMutableTreeNode {
  public static final String UNKNOWN_RANGE_COORDINATES = "(-, -) ";
  protected int findingCount;
  protected int fileCount;

  public abstract void render(TreeCellRenderer renderer);

  public boolean hasChildren() {
    return getChildCount() != 0;
  }

  public int getFindingCount() {
    if (findingCount < 0) {
      findingCount = 0;
      var children = super.children();

      while (children.hasMoreElements()) {
        var node = (AbstractNode) children.nextElement();
        if (node == null) {
          continue;
        }

        findingCount += node.getFindingCount();
      }
    }

    return findingCount;
  }

  @Override
  public void remove(int index) {
    setDirty();
    super.remove(index);
  }

  @Override
  public void remove(MutableTreeNode aChild) {
    setDirty();
    super.remove(aChild);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    setDirty();
    super.insert(newChild, childIndex);
  }

  @Override
  public void add(MutableTreeNode newChild) {
    setDirty();
    super.add(newChild);
  }

  public void setDirty() {
    fileCount = -1;
    findingCount = -1;
    if (super.getParent() != null) {
      ((AbstractNode) super.getParent()).setDirty();
    }
  }

  public String formatRangeMarker(@Nullable VirtualFile file, @Nullable RangeMarker rangeMarker) {
    if (rangeMarker == null) {
      return "(0, 0) ";
    }

    if (!rangeMarker.isValid() || file == null || !file.isValid()) {
      return UNKNOWN_RANGE_COORDINATES;
    }

    var cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file);
    if (cachedDocument == null) {
      return UNKNOWN_RANGE_COORDINATES;
    }

    var line = cachedDocument.getLineNumber(rangeMarker.getStartOffset());
    var offset = rangeMarker.getStartOffset() - cachedDocument.getLineStartOffset(line);
    return String.format("(%d, %d) ", line + 1, offset);
  }

  public void openFileFromRangeMarker(Project project, @Nullable RangeMarker rangeMarker) {
    if (rangeMarker == null || !rangeMarker.isValid()) {
      return;
    }

    runReadActionSafely(project, () -> {
      var psiFile = PsiDocumentManager.getInstance(project).getPsiFile(rangeMarker.getDocument());
      if (psiFile != null && psiFile.isValid()) {
        new OpenFileDescriptor(project, psiFile.getVirtualFile(), rangeMarker.getStartOffset()).navigate(false);
      }
    });
  }

  @NotNull
  public static String spaceAndThinSpace() {
    var thinSpace = UIUtil.getLabelFont().canDisplay('\u2009') ? String.valueOf('\u2009') : " ";
    return " " + thinSpace;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + hashCode();
  }
}
