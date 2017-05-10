/*
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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.sonarlint.intellij.messages.ServerIssuesListener;

import javax.annotation.CheckForNull;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerIssues extends AbstractProjectComponent {
  private final MessageBus messageBus;
  private Map<VirtualFile, Collection<LiveIssue>> issues;
  private LocalDateTime lastAnalysis;

  public ServerIssues(Project project) {
    super(project);
    issues = new HashMap<>();
    messageBus = project.getMessageBus();
    lastAnalysis = null;
  }

  public void set(LocalDateTime lastAnalysis, Map<VirtualFile, Collection<LiveIssue>> issues) {
    this.issues = Collections.unmodifiableMap(issues);
    this.lastAnalysis = lastAnalysis;
    this.messageBus.syncPublisher(ServerIssuesListener.SERVER_ISSUES_TOPIC).update(issues);
  }

  public void clear() {
    this.issues = Collections.unmodifiableMap(Collections.emptyMap());
    this.lastAnalysis = null;
    this.messageBus.syncPublisher(ServerIssuesListener.SERVER_ISSUES_TOPIC).update(issues);
  }

  public boolean wasAnalyzed() {
    return lastAnalysis != null;
  }

  @CheckForNull
  public LocalDateTime lastAnalysisDate() {
    return lastAnalysis;
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues;
  }
}
