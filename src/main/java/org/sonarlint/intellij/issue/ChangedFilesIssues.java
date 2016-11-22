package org.sonarlint.intellij.issue;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.messages.ChangedFilesIssuesListener;

public class ChangedFilesIssues extends AbstractProjectComponent {
  private Map<VirtualFile, Collection<LiveIssue>> issues;
  private LocalDateTime lastAnalysis;
  private MessageBus messageBus;

  public ChangedFilesIssues(Project project) {
    super(project);
    issues = new HashMap<>();
    messageBus = project.getMessageBus();
    lastAnalysis = null;
  }

  public void set(Map<VirtualFile, Collection<LiveIssue>> issues) {
    this.issues = Collections.unmodifiableMap(issues);
    this.lastAnalysis = LocalDateTime.now();
    this.messageBus.syncPublisher(ChangedFilesIssuesListener.CHANGED_FILES_ISSUES_TOPIC).update(issues);
  }

  public void clear() {
    this.issues = Collections.unmodifiableMap(Collections.emptyMap());
    this.lastAnalysis = null;
    this.messageBus.syncPublisher(ChangedFilesIssuesListener.CHANGED_FILES_ISSUES_TOPIC).update(issues);
  }

  @CheckForNull
  public LocalDateTime lastAnalysisDate() {
    return lastAnalysis;
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues;
  }
}
