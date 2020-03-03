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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class DefaultClientInputFile implements ClientInputFile {
  private final String path;
  private final String relativePath;
  private final boolean test;
  private final Charset charset;
  private final VirtualFile vFile;
  private final Document doc;
  private final URI uri;

  DefaultClientInputFile(VirtualFile vFile, String relativePath, boolean isTest, Charset charset, @Nullable Document doc) {
    this.path = vFile.getPath();
    this.relativePath = relativePath;
    this.test = isTest;
    this.charset = charset;
    this.vFile = vFile;
    this.doc = doc;
    this.uri = createURI();
  }

  DefaultClientInputFile(VirtualFile vFile, String relativePath, boolean isTest, Charset charset) {
    this(vFile, relativePath, isTest, charset, null);
  }

  @Override public String getPath() {
    return path;
  }

  @Override
  public String relativePath() {
    return relativePath;
  }

  @Override public boolean isTest() {
    return test;
  }

  @Override public Charset getCharset() {
    return charset;
  }

  @Override public InputStream inputStream() throws IOException {
    if (doc == null) {
      return vFile.getInputStream();
    }

    return new ByteArrayInputStream(doc.getText().getBytes(charset));
  }

  private URI createURI() {
    // Don't use VfsUtilCore.convertToURL as it doesn't work on Windows (it produces invalid URL)
    // Instead, since we are currently limiting ourself to analyze files in LocalFileSystem
    if (!vFile.isInLocalFileSystem()) {
      throw new IllegalStateException("Not a local file");
    }
    return Paths.get(vFile.getPath()).toUri();
  }

  @Override public String contents() throws IOException {
    if (doc == null) {
      return new String(vFile.contentsToByteArray(), charset);
    }
    return doc.getText();
  }

  @Override public VirtualFile getClientObject() {
    return vFile;
  }

  @Override
  public URI uri() {
    return uri;
  }
}
