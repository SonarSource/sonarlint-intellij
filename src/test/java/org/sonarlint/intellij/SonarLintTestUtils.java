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
package org.sonarlint.intellij;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import org.sonarlint.intellij.analysis.SonarLintFacade;
import com.intellij.openapi.project.Project;
import org.sonarsource.sonarlint.core.IssueListener;

import java.awt.GraphicsEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarLintTestUtils {
  static {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    System.out.println("headless mode: " + ge.isHeadless());
  }

  private SonarLintTestUtils() {
    // only static
  }

  public static SonarLintFacade mockRunner(Project project) {
    return mockInContainer(SonarLintFacade.class, project);
  }

  public static <T> T mockInContainer(Class<T> clazz, Project project) {
    T mocked = mock(clazz);
    ComponentManagerImpl compManager = (ComponentManagerImpl) project;
    compManager.registerComponentInstance(clazz, mocked);
    return mocked;
  }

  public static AnActionEvent createAnActionEvent(Project project) {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    return event;
  }

  public static IssueListener.Issue createIssue(int id) {
    IssueListener.Issue issue = new IssueListener.Issue();
    issue.setRuleKey(Integer.toString(id));
    issue.setMessage("issue " + id);
    return issue;
  }
}
