/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonar.duplications.block.Block;
import org.sonar.duplications.block.BlockChunker;
import org.sonar.duplications.java.JavaStatementBuilder;
import org.sonar.duplications.java.JavaTokenProducer;
import org.sonar.duplications.statement.Statement;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.ModuleAnalysisResult;
import org.sonarlint.intellij.analysis.cpd.Duplication;
import org.sonarlint.intellij.analysis.cpd.DuplicationReport;
import org.sonarlint.intellij.analysis.cpd.SonarCpdBlockIndex;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

public class ShowAnalysisResultsCallable implements AnalysisCallback {
  private final Project project;
  private final Collection<VirtualFile> affectedFiles;
  private final String whatAnalyzed;

  public ShowAnalysisResultsCallable(Project project, Collection<VirtualFile> affectedFiles, String whatAnalyzed) {
    this.project = project;
    this.affectedFiles = affectedFiles;
    this.whatAnalyzed = whatAnalyzed;
  }

  @Override public void onError(Throwable e) {
    // nothing to do
  }

  @Override
  public void onSuccess(List<ModuleAnalysisResult> results, Set<VirtualFile> failedVirtualFiles) {
    var issueManager = SonarLintUtils.getService(project, IssueManager.class);
    var map = affectedFiles.stream()
      .filter(f -> !failedVirtualFiles.contains(f))
      .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
    var issueStore = SonarLintUtils.getService(project, IssueStore.class);
    issueStore.set(map, whatAnalyzed);
    showAnalysisResultsTab();
    displayDuplications(project, results.stream().flatMap(r -> r.getInputFiles().stream()).collect(Collectors.toList()));
  }

  private void showAnalysisResultsTab() {
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, SonarLintToolWindow.class)
      .openAnalysisResults());
  }

  private static void displayDuplications(Project project, Collection<ClientInputFile> allFiles) {
    var console = SonarLintConsole.get(project);
    var report = computeDuplications(allFiles, console);
    if (report.hasAnyDuplication()) {
      console.info("No duplication found during analysis");
    } else {
      console.info("Some duplications were found during analysis: " + report.getBlockDuplications());
      console.info("Duplication density is: " + report.getDensity());
    }
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, SonarLintToolWindow.class)
      .showDuplicationDensityInReport(report));
  }

  private static final int BLOCK_SIZE = 4;

  private static DuplicationReport computeDuplications(Collection<ClientInputFile> inputFiles, SonarLintConsole console) {
    var index = new SonarCpdBlockIndex();
    indexBlocks(index, inputFiles, console);
    var blockDuplications = findDuplications(index);
    var density = computeDuplicationDensity(inputFiles, blockDuplications);
    return new DuplicationReport(blockDuplications, density);
  }

  private static void indexBlocks(SonarCpdBlockIndex index, Collection<ClientInputFile> sourceFiles, SonarLintConsole console) {
    var tokenChunker = JavaTokenProducer.build();
    var statementChunker = JavaStatementBuilder.build();
    var blockChunker = new BlockChunker(BLOCK_SIZE);

    for (ClientInputFile inputFile : sourceFiles) {
      if (!inputFile.relativePath().endsWith(".java")) {
        continue;
      }
      console.debug("Populating index from " + inputFile);
      var resourceEffectiveKey = inputFile.uri().toString();

      List<Statement> statements;

      try (var is = inputFile.inputStream();
           Reader reader = new InputStreamReader(is, inputFile.getCharset())) {
        statements = statementChunker.chunk(tokenChunker.chunk(reader));
      } catch (FileNotFoundException e) {
        throw new IllegalStateException("Cannot find file " + inputFile.relativePath(), e);
      } catch (IOException e) {
        throw new IllegalStateException("Exception handling file: " + inputFile.relativePath(), e);
      }

      List<Block> blocks;
      try {
        blocks = blockChunker.chunk(resourceEffectiveKey, statements);
      } catch (Exception e) {
        throw new IllegalStateException("Cannot process file " + inputFile.relativePath(), e);
      }
      index.insert(inputFile, blocks);
    }
  }

  private static List<Duplication> findDuplications(SonarCpdBlockIndex index) {
    var duplications = new ArrayList<Duplication>();
    index.getUniqueBlockHashes().forEach(hash -> {
      var files = index.getFilesWithBlockHash(hash);
      if (!files.isEmpty()) {
        var occurrences = files.stream()
          .map(f -> new Duplication.Occurrence(f, index.getBlocks(f, hash)))
          .collect(Collectors.toList());
        if (occurrences.size() > 1) {
          duplications.add(new Duplication(occurrences));
        }
      }
    });
    return duplications;
  }

  private static float computeDuplicationDensity(Collection<ClientInputFile> inputFiles, List<Duplication> blockDuplications) {
    var totalLines = inputFiles.stream().mapToInt(ShowAnalysisResultsCallable::countLines).sum();
    var duplicatedLinesCount = blockDuplications.stream().mapToInt(Duplication::getInvolvedLinesCount).sum();
    return totalLines != 0 ? (duplicatedLinesCount / (float) totalLines) : 0;
  }

  private static int countLines(ClientInputFile inputFile) {
    try {
      return inputFile.contents().split("\\r?\\n").length;
    } catch (IOException e) {
      return 0;
    }
  }
}
