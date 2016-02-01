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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import static org.sonarsource.sonarlint.core.IssueListener.Issue;

import javax.annotation.Nullable;
import java.nio.file.Path;

public class IssueMatcher extends AbstractProjectComponent {
  private final PsiManager psiManager;
  private final PsiDocumentManager docManager;

  public IssueMatcher(Project project, PsiManager psiManager, PsiDocumentManager docManager) {
    super(project);
    this.psiManager = psiManager;
    this.docManager = docManager;
  }

  public PsiFile findFile(VirtualFileSystem fs, Issue issue) throws NoMatchException {
    return findFile(fs, issue.getFilePath());
  }

  public PsiFile findFile(VirtualFileSystem fs, Path path) throws NoMatchException {
    VirtualFile file = fs.findFileByPath(path.toString());

    if (file != null) {
      return findFile(file);
    }
    throw new NoMatchException("Couldn't find file in module: " + path.toString());
  }

  public PsiFile findFile(VirtualFile file) throws NoMatchException {
    PsiFile psiFile = psiManager.findFile(file);
    if (psiFile != null) {
      return psiFile;
    }
    throw new NoMatchException("Couldn't find PSI file in module: " + file.getPath());
  }

  /**
   * Tries to match an SQ issue to an IntelliJ file, by either:
   * - Attaching to the file with a dynamic {@link RangeMarker} (see {@link #createRangeIssue})
   * - Create a file-level issue (see {@link #createFileIssue})
   *
   * <b>Can only be called with read access</b>.
   */
  public IssuePointer match(PsiFile file, Issue issue) throws NoMatchException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Document doc = docManager.getDocument(file);
    if (doc == null) {
      throw new NoMatchException("No document found for file: " + file.getName());
    }

    if (issue.getStartLine() != null) {
      TextRange issueRange = getIssueTextRange(file, doc, issue);
      return createRangeIssue(file, doc, issue, issueRange);
    } else {
      // no start line, so it's probably a file issue
      return createFileIssue(file, issue);
    }
  }

  private static IssuePointer createRangeIssue(PsiFile file, Document doc, Issue issue, TextRange issueRange) {
    RangeMarker range = doc.createRangeMarker(issueRange.getStartOffset(), issueRange.getEndOffset());
    return new IssuePointer(issue, file, range);
  }

  private static IssuePointer createFileIssue(PsiFile file, Issue issue) {
    return new IssuePointer(issue, file);
  }

  private static TextRange getIssueTextRange(PsiFile file, Document doc, Issue issue) throws NoMatchException {
    int ijStartLine = issue.getStartLine() - 1;
    int ijEndLine = issue.getEndLine() - 1;
    int lineCount = doc.getLineCount();

    if (ijEndLine > doc.getLineCount()) {
      throw new NoMatchException("Line number (" + ijStartLine + ") larger than lines in file: " + lineCount);
    }

    int rangeStart = findStartLineOffset(file, doc, ijStartLine, issue.getStartLineOffset());
    int rangeEnd = findEndLineOffset(doc, ijEndLine, issue.getEndLineOffset());

    if (rangeEnd < rangeStart) {
      throw new NoMatchException("Invalid Text Range");
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

  private static int findStartLineOffset(PsiFile file, Document doc, int ijLine, @Nullable Integer startOffset) {
    int ijStartOffset = (startOffset == null) ? 0 : startOffset;
    int lineStartOffset = doc.getLineStartOffset(ijLine);
    int rangeStart = lineStartOffset + ijStartOffset;

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

    // we got to another line, don't use it
    if (doc.getLineNumber(nextRangeStart) != ijLine) {
      return rangeStart;
    }

    return nextRangeStart;
  }

  public static class NoMatchException extends Exception {
    public NoMatchException() {
      // nothing to do
    }

    public NoMatchException(String msg) {
      super(msg);
    }
  }

}
