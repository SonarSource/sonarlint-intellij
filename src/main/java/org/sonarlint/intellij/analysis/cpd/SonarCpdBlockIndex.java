/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.intellij.analysis.cpd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.duplications.block.Block;
import org.sonar.duplications.block.ByteArray;
import org.sonar.duplications.index.AbstractCloneIndex;
import org.sonar.duplications.index.CloneIndex;
import org.sonar.duplications.index.PackedMemoryCloneIndex;
import org.sonar.duplications.index.PackedMemoryCloneIndex.ResourceBlocks;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

public class SonarCpdBlockIndex extends AbstractCloneIndex {
  private static final Logger LOG = Loggers.get(SonarCpdBlockIndex.class);
  private final CloneIndex mem = new PackedMemoryCloneIndex();
  // Files already tokenized
  private final Map<String, Set<ClientInputFile>> filesHavingBlockHash = new HashMap<>();

  public void insert(ClientInputFile inputFile, Collection<Block> blocks) {
    // if (settings.isCrossProjectDuplicationEnabled()) {
    // int id = ((DefaultInputFile) inputFile).scannerId();
    // if (publisher.getWriter().hasComponentData(FileStructure.Domain.CPD_TEXT_BLOCKS, id)) {
    // throw new UnsupportedOperationException("Trying to save CPD tokens twice for the same file is not supported: " +
    // inputFile.absolutePath());
    // }
    // final ScannerReport.CpdTextBlock.Builder builder = ScannerReport.CpdTextBlock.newBuilder();
    // publisher.getWriter().writeCpdTextBlocks(id, blocks.stream().map(block -> {
    // builder.clear();
    // builder.setStartLine(block.getStartLine());
    // builder.setEndLine(block.getEndLine());
    // builder.setStartTokenIndex(block.getStartUnit());
    // builder.setEndTokenIndex(block.getEndUnit());
    // builder.setHash(block.getBlockHash().toHexString());
    // return builder.build();
    // }).collect(Collectors.toList()));
    // }
    for (Block block : blocks) {
      mem.insert(block);
      var files = filesHavingBlockHash.computeIfAbsent(block.getHashHex(), k -> new HashSet<>());
      files.add(inputFile);
    }
    if (blocks.isEmpty()) {
      LOG.debug("Not enough content in '{}' to have CPD blocks, it will not be part of the duplication detection", inputFile.relativePath());
    }
  }

  @Override
  public Collection<Block> getBySequenceHash(ByteArray hash) {
    return mem.getBySequenceHash(hash);
  }

  public Set<String> getUniqueBlockHashes() {
    return filesHavingBlockHash.keySet();
  }

  public Collection<ClientInputFile> getFilesWithBlockHash(String hash) {
    return filesHavingBlockHash.getOrDefault(hash, new HashSet<>());
  }

  @Override
  public Collection<Block> getByResourceId(String resourceId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insert(Block block) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<ResourceBlocks> iterator() {
    return mem.iterator();
  }

  @Override
  public int noResources() {
    return mem.noResources();
  }

  public List<Block> getBlocks(ClientInputFile inputFile, String blockHash) {
    return mem.getByResourceId(inputFile.uri().toString())
      .stream().filter(b -> b.getHashHex().equals(blockHash))
      .collect(Collectors.toList());
  }
}
