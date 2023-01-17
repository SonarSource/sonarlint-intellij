/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.ui.fixtures;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;

public class SonarLintToolWindowFixture {
  public static SonarLintToolWindowFixture createFor(Project project) {
    var manager = new ToolWindowManagerImpl(project) {
      @Override
      protected void fireStateChanged() {
      }
    };
    var frame = new ProjectFrameHelper(new IdeFrameImpl(), null);
    frame.init();
    manager.init(frame);
    for (var extension : ToolWindowEP.EP_NAME.getExtensionList()) {
      if (SonarLintToolWindowFactory.TOOL_WINDOW_ID.equals(extension.id)) {
        manager.initToolWindow(extension);
      }
    }
    return new SonarLintToolWindowFixture(manager);
  }

  private final ToolWindowManagerImpl manager;

  public SonarLintToolWindowFixture(ToolWindowManagerImpl manager) {
    this.manager = manager;
  }

  public ToolWindowManagerImpl getManager() {
    return manager;
  }

  public void cleanUp() {
    Disposer.dispose(manager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID).getContentManager());
  }

}
