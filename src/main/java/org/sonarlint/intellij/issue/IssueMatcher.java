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

import com.google.common.base.Preconditions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

public class IssueMatcher extends AbstractProjectComponent {
  private final PsiManager psiManager;
  private final PsiDocumentManager docManager;

  public IssueMatcher(Project project, PsiManager psiManager, PsiDocumentManager docManager) {
    super(project);
    this.psiManager = psiManager;
    this.docManager = docManager;
  }

  public PsiFile findFile(VirtualFile file) throws NoMatchException {
    PsiFile psiFile = psiManager.findFile(file);
    if (psiFile != null) {
      return psiFile;
    }
    throw new NoMatchException("Couldn't find PSI file in module: " + file.getPath());
  }

  /**
   * Tries to match an SQ issue to an IntelliJ file.
   * <b>Can only be called with getLive access</b>.
   */
  public RangeMarker match(PsiFile file, IssueLocation issueLocation) throws NoMatchException {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Preconditions.checkArgument(issueLocation.getStartLine() != null);

    Document doc = docManager.getDocument(file);
    if (doc == null) {
      throw new NoMatchException("No document found for file: " + file.getName());
    }

    TextRange range = getIssueTextRange(file, doc, issueLocation);
    return doc.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  private static TextRange getIssueTextRange(PsiFile file, Document doc, IssueLocation issueLocation) throws NoMatchException {
    int ijStartLine = issueLocation.getStartLine() - 1;
    int ijEndLine = issueLocation.getEndLine() - 1;
    int lineCount = doc.getLineCount();

    if (ijStartLine >= doc.getLineCount()) {
      throw new NoMatchException("Start line number (" + ijStartLine + ") larger than lines in file: " + lineCount);
    }
    if (ijEndLine >= doc.getLineCount()) {
      throw new NoMatchException("End line number (" + ijStartLine + ") larger than lines in file: " + lineCount);
    }

    int rangeEnd = findEndLineOffset(doc, ijEndLine, issueLocation.getEndLineOffset());
    int rangeStart = findStartLineOffset(file, doc, ijStartLine, issueLocation.getStartLineOffset(), rangeEnd);

    if (rangeEnd < rangeStart) {
      throw new NoMatchException("Invalid text range  (start: " + rangeStart + ", end: " + rangeEnd);
    }
    return new TextRange(rangeStart, rangeEnd);
  }

  private static int findEndLineOffset(Document doc, int ijLine, @Nullable Integer endOffset) {
    int lineEnd = doc.getLineEndOffset(ijLine);
    int lineStart = doc.getLineStartOffset(ijLine);
    int lineLength = lineEnd - lineStart;

    if (endOffset == null || endOffset > lineLength) {
      return lineEnd;
    }

    return lineStart + endOffset;
  }

  private static int findStartLineOffset(PsiFile file, Document doc, int ijLine, @Nullable Integer startOffset, int rangeEnd) {
    int ijStartOffset = (startOffset == null) ? 0 : startOffset;
    int lineStart = doc.getLineStartOffset(ijLine);
    int rangeStart = lineStart + ijStartOffset;

    if (rangeStart >= rangeEnd) {
      // we passed end
      return rangeEnd;
    }

    if (ijStartOffset != 0) {
      // this is a precise issue location, accept it as it is
      return rangeStart;
    }

    // probably not precise issue location. Try to match next element if it's whitespace.
    PsiElement el = file.getViewProvider().findElementAt(rangeStart);

    if (!(el instanceof PsiWhiteSpace)) {
      return rangeStart;
    }

    PsiElement next = el.getNextSibling();
    if (next == null) {
      return rangeStart;
    }

    int nextRangeStart = next.getTextRange().getStartOffset();

    if (nextRangeStart >= rangeEnd) {
      // we passed the end, don't use it
      return rangeStart;
    }

    if (doc.getLineNumber(nextRangeStart) != ijLine) {
      // we got to another line, don't use it
      return rangeStart;
    }

    return nextRangeStart;
  }

  public static class NoMatchException extends Exception {
    public NoMatchException(String msg) {
      super(msg);
    }
  }
}
