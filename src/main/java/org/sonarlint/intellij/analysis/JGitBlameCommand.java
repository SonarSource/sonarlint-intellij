package org.sonarlint.intellij.analysis;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawTextComparator;
import org.sonar.api.batch.scm.BlameLine;

import static java.util.Collections.emptyList;

public class JGitBlameCommand {

  public List<BlameLine> blame(Git git, String filename) {
    BlameResult blameResult;
    try {
      blameResult = git.blame()
        // Equivalent to -w command line option
        .setTextComparator(RawTextComparator.WS_IGNORE_ALL)
        .setFilePath(filename).call();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to blame file " + filename, e);
    }
    List<BlameLine> lines = new ArrayList<>();
    if (blameResult == null) {
      return emptyList();
    }
    for (int i = 0; i < blameResult.getResultContents().size(); i++) {
      if (blameResult.getSourceAuthor(i) == null || blameResult.getSourceCommit(i) == null) {
        return emptyList();
      }
      lines.add(new BlameLine()
        .date(blameResult.getSourceCommitter(i).getWhen())
        .revision(blameResult.getSourceCommit(i).getName())
        .author(blameResult.getSourceAuthor(i).getEmailAddress()));
    }

    return lines;
  }

}
