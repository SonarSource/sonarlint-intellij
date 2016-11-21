package org.sonarlint.intellij.messages;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.issue.LiveIssue;

public interface ChangedFilesIssuesListener {
  Topic<ChangedFilesIssuesListener> CHANGED_FILES_ISSUES_TOPIC = Topic.create("Changed files issues changed", ChangedFilesIssuesListener.class);

  void update(Map<VirtualFile, Collection<LiveIssue>> issues);
}
