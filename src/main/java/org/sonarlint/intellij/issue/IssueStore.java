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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.messages.AnalysisResultsListener;

public abstract class IssueStore extends AbstractProjectComponent {
  private final MessageBus messageBus;
  private Map<VirtualFile, Collection<LiveIssue>> issues;
  private String whatAnalyzed;
  private Instant lastAnalysis;

  protected IssueStore(Project project) {
    super(project);
    issues = new HashMap<>();
    messageBus = project.getMessageBus();
  }

  public void set(Map<VirtualFile, Collection<LiveIssue>> issues, String whatAnalyzed) {
    this.issues = Collections.unmodifiableMap(issues);
    this.whatAnalyzed = whatAnalyzed;
    this.lastAnalysis = Instant.now();
    this.messageBus.syncPublisher(getTopic()).update(issues);
  }

  public void clear() {
    this.issues = Collections.unmodifiableMap(Collections.emptyMap());
    this.lastAnalysis = null;
    this.messageBus.syncPublisher(getTopic()).update(issues);
  }

  @CheckForNull
  public String whatAnalyzed() {
    return whatAnalyzed;
  }

  public boolean wasAnalyzed() {
    return lastAnalysis != null;
  }

  @CheckForNull
  public Instant lastAnalysisDate() {
    return lastAnalysis;
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues;
  }

  protected abstract Topic<AnalysisResultsListener> getTopic();
}
