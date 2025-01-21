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
package org.sonarlint.intellij.finding;

import com.google.common.base.Preconditions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static org.sonarlint.intellij.util.ProjectUtils.toPsiFile;

public class TextRangeMatcher {
  private final Project project;

  public TextRangeMatcher(Project project) {
    this.project = project;
  }

  /**
   * Tries to match an SQ issue to an IntelliJ file.
   * <b>Can only be called with getLive access</b>.
   */
  public RangeMarker match(VirtualFile file, TextRangeWithHashDto textRange) throws NoMatchException {
    return match(toPsiFile(project, file), textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  public RangeMarker matchWithCode(VirtualFile file, TextRangeDto textRange, String codeSnippet) throws NoMatchException {
    var psiFile = toPsiFile(project, file);
    var docManager = PsiDocumentManager.getInstance(project);
    var doc = docManager.getDocument(psiFile);
    if (doc == null) {
      throw new NoMatchException("No document found for file: " + file.getName());
    }
    var range = getIssueTextRange(psiFile, doc, textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
    var codeAtRange = doc.getText(range);
    if (!codeAtRange.equals(codeSnippet)) {
      return null;
    }
    return doc.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  public RangeMarker match(PsiFile file, TextRangeDto textRange) throws NoMatchException {
    return match(file, textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  private RangeMarker match(PsiFile file, @Nullable Integer startLine, @Nullable Integer startLineOffset, @Nullable Integer endLine, @Nullable Integer endLineOffset)
    throws NoMatchException {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Preconditions.checkArgument(startLine != null);

    var docManager = PsiDocumentManager.getInstance(project);
    var doc = docManager.getDocument(file);
    if (doc == null) {
      throw new NoMatchException("No document found for file: " + file.getName());
    }

    var range = getIssueTextRange(file, doc, startLine, startLineOffset, endLine, endLineOffset);
    return doc.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  private static TextRange getIssueTextRange(PsiFile file, Document doc, @Nullable Integer startLine, @Nullable Integer startLineOffset, @Nullable Integer endLine,
    @Nullable Integer endLineOffset) throws NoMatchException {
    if (startLine != null && endLine != null && startLineOffset != null && endLineOffset != null && "ipynb".equals(file.getVirtualFile().getExtension())) {
      var newTextRange = computeTextRangeForNotebook(doc.getText(), startLine, startLineOffset, endLine, endLineOffset);
      startLine = newTextRange.getStartLine();
      startLineOffset = newTextRange.getStartLineOffset();
      endLine = newTextRange.getEndLine();
      endLineOffset = newTextRange.getEndLineOffset();
    }

    if (startLine == null || endLine == null) {
      throw new NoMatchException("Start line and end line should not be null");
    }

    var ijStartLine = startLine - 1;
    var ijEndLine = endLine - 1;
    var lineCount = doc.getLineCount();

    if (ijStartLine >= doc.getLineCount()) {
      throw new NoMatchException("Start line number (" + ijStartLine + ") larger than lines in file: " + lineCount);
    }
    if (ijEndLine >= doc.getLineCount()) {
      throw new NoMatchException("End line number (" + ijStartLine + ") larger than lines in file: " + lineCount);
    }

    var rangeEnd = findEndLineOffset(doc, ijEndLine, endLineOffset);
    var rangeStart = findStartLineOffset(file, doc, ijStartLine, startLineOffset, rangeEnd);

    if (rangeEnd < rangeStart) {
      throw new NoMatchException("Invalid text range  (start: " + rangeStart + ", end: " + rangeEnd);
    }
    return new TextRange(rangeStart, rangeEnd);
  }

  public static TextRangeDto computeTextRangeForNotebook(String fileContent, int prevStartLine,
    int prevStartLineOffset, int prevEndLine, int prevEndLineOffset) {
    var isMarkdown = false;
    var startLine = prevStartLine;
    var endLine = prevEndLine;
    var lineNumber = 0;
    for (var line : fileContent.lines().toList()) {
      if (line.startsWith("#%% md") || line.startsWith("#%% raw")) {
        isMarkdown = true;
      } else if (line.startsWith("#%%")) {
        isMarkdown = false;
      }

      if (isMarkdown && startLine > lineNumber) {
        startLine++;
        endLine++;
      }
      lineNumber++;
    }
    return new TextRangeDto(startLine, prevStartLineOffset, endLine, prevEndLineOffset);
  }

  private static int findEndLineOffset(Document doc, int ijLine, @Nullable Integer endOffset) {
    var lineEnd = doc.getLineEndOffset(ijLine);
    var lineStart = doc.getLineStartOffset(ijLine);
    var lineLength = lineEnd - lineStart;

    if (endOffset == null || endOffset > lineLength) {
      return lineEnd;
    }

    return lineStart + endOffset;
  }

  private static int findStartLineOffset(PsiFile file, Document doc, int ijLine, @Nullable Integer startOffset, int rangeEnd) {
    var ijStartOffset = (startOffset == null) ? 0 : startOffset;
    var lineStart = doc.getLineStartOffset(ijLine);
    var rangeStart = lineStart + ijStartOffset;

    if (rangeStart >= rangeEnd) {
      // we passed end
      return rangeEnd;
    }

    if (startOffset != null) {
      // this is a precise issue location, accept it as it is
      return rangeStart;
    }

    // probably not precise issue location. Try to match next element if it's whitespace.
    var el = file.getViewProvider().findElementAt(rangeStart);

    if (!(el instanceof PsiWhiteSpace)) {
      return rangeStart;
    }

    var next = el.getNextSibling();
    if (next == null) {
      return rangeStart;
    }

    var nextRangeStart = next.getTextRange().getStartOffset();

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
