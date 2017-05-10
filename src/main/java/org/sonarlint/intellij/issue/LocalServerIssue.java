/*
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


import com.intellij.openapi.vfs.VirtualFile;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

public class LocalServerIssue implements Issue {
    private ScannerInput.ServerIssue issue;
    private VirtualFile virtualFile;

    public LocalServerIssue(ScannerInput.ServerIssue issue, VirtualFile virtualFile) {
        this.issue = issue;
        this.virtualFile = virtualFile;
    }

    @Override
    public String getSeverity() {
        return issue.getSeverity() != null ? issue.getSeverity().name() : null;
    }

    @Override
    public String getType() {
        return !issue.getType().isEmpty() ? issue.getType() : null;
    }

    @Override
    public String getMessage() {
        return issue.getMsg();
    }

    @Override
    public String getRuleKey() {
        return issue.getRuleRepository() + ":" + issue.getRuleKey();
    }

    @Override
    public String getRuleName() {
        return issue.getRuleKey();
    }

    @Override
    public Integer getStartLine() {
        return issue.getLine() != 0 ? issue.getLine() : null;
    }

    @Override
    public Integer getStartLineOffset() {
        return null;
    }

    @Override
    public Integer getEndLine() {
        return issue.getLine() != 0 ? issue.getLine() : null;
    }

    @Override
    public Integer getEndLineOffset() {
        return null;
    }

    @Override
    public List<Flow> flows() {
        return Collections.emptyList();
    }

    @Override
    public ClientInputFile getInputFile() {
        if (virtualFile == null) {
            return null;
        }
        return new ClientInputFile() {
            @Override
            public String getPath() {
                return virtualFile.getPath();
            }

            @Override
            public boolean isTest() {
                return false;
            }

            @Override
            public Charset getCharset() {
                return virtualFile.getCharset();
            }

            @Override
            public VirtualFile getClientObject() {
                return virtualFile;
            }

            @Override
            public InputStream inputStream() throws IOException {
                return virtualFile.getInputStream();
            }

            @Override
            public String contents() throws IOException {
                return new String(virtualFile.contentsToByteArray(), virtualFile.getCharset());
            }
        };
    }
}
