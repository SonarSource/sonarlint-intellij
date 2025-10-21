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
package org.sonarlint.intellij.finding.issue;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.finding.FindingContext;
import org.sonarlint.intellij.finding.Issue;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.QuickFix;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class LiveIssue extends LiveFinding implements Issue {

  @Nullable
  private final RuleType type;
  private final boolean isAiCodeFixable;
  @Nullable
  private final ResolutionStatus status;

  public LiveIssue(Module module, RaisedIssueDto issue, VirtualFile virtualFile, List<QuickFix> quickFixes) {
    this(module, issue, virtualFile, null, null, quickFixes);
  }

  public LiveIssue(Module module, RaisedIssueDto issue, VirtualFile virtualFile, @Nullable RangeMarker range, @Nullable FindingContext context, List<QuickFix> quickFixes) {
    super(module, issue, virtualFile, range, context, quickFixes);
    if (issue.getSeverityMode().isLeft()) {
      this.type = issue.getSeverityMode().getLeft().getType();
    } else {
      this.type = null;
    }
    this.isAiCodeFixable = issue.isAiCodeFixable();
    this.status = issue.getResolutionStatus();
  }

  @CheckForNull
  @Override
  public RuleType getType() {
    return type;
  }

  @Override
  public void resolve() {
    setResolved(true);
  }

  @Override
  public void reopen() {
    setResolved(false);
  }

  public boolean isAiCodeFixable() {
    var hasNoQuickFix = quickFixes().stream().noneMatch(QuickFix::isSingleFile);
    return this.isAiCodeFixable && hasNoQuickFix;
  }

  @Nullable
  public ResolutionStatus getStatus() {
    return status;
  }
}
