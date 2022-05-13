package org.sonarlint.intellij.analysis.cpd;

import java.util.List;
import org.sonar.duplications.block.Block;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

public class Duplication {
  private final List<Occurrence> occurrences;

  public Duplication(List<Occurrence> occurrences) {
    this.occurrences = occurrences;
  }

  public int getInvolvedLinesCount() {
    return occurrences.stream().mapToInt(Occurrence::getInvolvedLinesCount).sum();
  }

  public List<Occurrence> getOccurrences() {
    return occurrences;
  }

  @Override
  public String toString() {
    return "Duplication{" +
      "occurrences=" + occurrences +
      '}';
  }

  public static class Occurrence {
    private final ClientInputFile inputFile;
    private final List<Block> blocks;

    public Occurrence(ClientInputFile inputFile, List<Block> blocks) {
      this.inputFile = inputFile;
      this.blocks = blocks;
    }

    public ClientInputFile getInputFile() {
      return inputFile;
    }

    public List<Block> getBlocks() {
      return blocks;
    }

    public int getInvolvedLinesCount() {
      return blocks.stream().mapToInt(b -> b.getEndLine() - b.getStartLine() + 1).sum();
    }

    @Override
    public String toString() {
      return "Occurrence{" +
        "inputFile=" + inputFile +
        ", blocks=" + blocks +
        '}';
    }
  }

}
