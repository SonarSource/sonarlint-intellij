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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.util.VirtualFileUtils;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class DefaultClientInputFile implements ClientInputFile {
  private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

  private final String path;
  private final String relativePath;
  private final boolean test;
  private final Charset charset;
  private final VirtualFile vFile;
  @Nullable
  private final String documentBuffer;
  @Nullable
  private final SonarLanguage language;
  private final URI uri;
  private final long documentModificationStamp;

  public DefaultClientInputFile(VirtualFile vFile, String relativePath, boolean isTest, Charset charset, @Nullable String documentBuffer, long documentModificationStamp,
    @Nullable ForcedLanguage sonarLanguage) {
    this.path = vFile.getPath();
    this.relativePath = relativePath;
    this.test = isTest;
    this.charset = charset;
    this.vFile = vFile;
    this.documentBuffer = documentBuffer;
    this.documentModificationStamp = documentModificationStamp;
    this.language = sonarLanguage == null ? null : SonarLanguage.valueOf(sonarLanguage.name());
    this.uri = createURI(vFile);
  }

  public DefaultClientInputFile(VirtualFile vFile, String relativePath, boolean isTest, Charset charset) {
    this(vFile, relativePath, isTest, charset, null, 0, null);
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
    if (documentBuffer == null) {
      return vFile.getInputStream();
    }

    return new ByteArrayInputStream(documentBuffer.getBytes(charset));
  }

  private static URI createURI(VirtualFile file) {
    var uri = VirtualFileUtils.INSTANCE.toURI(file);
    if (uri == null) {
      throw new IllegalStateException("Not a local file");
    }
    return uri;
  }

  @Override public String contents() throws IOException {
    if (documentBuffer == null) {
      var result = new ByteArrayOutputStream();
      try (var inputStream = inputStream()) {
        var buffer = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
          result.write(buffer, 0, length);
        }
      }
      return result.toString(charset);
    }
    return documentBuffer;
  }

  @Override public VirtualFile getClientObject() {
    return vFile;
  }

  @Override
  public URI uri() {
    return uri;
  }

  @Override
  public SonarLanguage language() {
    return language;
  }
}
