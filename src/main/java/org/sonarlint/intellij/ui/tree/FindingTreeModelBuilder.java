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
package org.sonarlint.intellij.ui.tree;

import javax.annotation.CheckForNull;
import org.sonarlint.intellij.ui.nodes.AbstractNode;

public interface FindingTreeModelBuilder {

  @CheckForNull
  default AbstractNode getPreviousNode(AbstractNode startNode) {
    var parent = (AbstractNode) startNode.getParent();

    if (parent == null) {
      return null;
    }
    var previous = parent.getChildBefore(startNode);
    if (previous == null) {
      return getPreviousNode(parent);
    }

    return (AbstractNode) previous;
  }

  /**
   * Next node, either the sibling if it exists, or the sibling of the parent
   */
  @CheckForNull
  default AbstractNode getNextNode(AbstractNode startNode) {
    var parent = (AbstractNode) startNode.getParent();

    if (parent == null) {
      return null;
    }
    var after = parent.getChildAfter(startNode);
    if (after == null) {
      return getNextNode(parent);
    }

    return (AbstractNode) after;
  }

}
