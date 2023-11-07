/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.finding.FindingContext;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.QuickFix;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class LiveIssue extends LiveFinding implements org.sonarlint.intellij.finding.Issue {

  private RuleType type;

  public LiveIssue(Module module, RawIssue issue, VirtualFile virtualFile, List<QuickFix> quickFixes) {
    this(module, issue, virtualFile, null, null, quickFixes);
  }

  public LiveIssue(Module module, RawIssue issue, VirtualFile virtualFile, @Nullable RangeMarker range, @Nullable FindingContext context, List<QuickFix> quickFixes) {
    super(module, issue, virtualFile, range, context, quickFixes);
    this.type = issue.getType();
  }

  @NotNull
  @Override
  public RuleType getType() {
    return type;
  }

  public void setType(@Nullable RuleType type) {
    this.type = type;
  }

  @Override
  public void resolve() {
    setResolved(true);
  }

  @Override
  public void reopen() {
    setResolved(false);
  }

}
