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
package org.sonarlint.intellij.errorsubmitter;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.util.Consumer;
import java.awt.Component;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.util.SonarLintUtils;

// Inspired from https://github.com/openclover/clover/blob/master/clover-idea/src/com/atlassian/clover/idea/util/BlameClover.java
public class BlameSonarSource extends ErrorReportSubmitter {
  private static final int MAX_URI_LENGTH = 4096;
  private static final int BUG_FAULT_CATEGORY_ID = 6;
  private static final String INTELLIJ_TAG = "intellij";
  private static final String COMMUNITY_ROOT_URL = "https://community.sonarsource.com/";
  private static final String COMMUNITY_FAULT_CATEGORY_URL = COMMUNITY_ROOT_URL + "tags/c/" + BUG_FAULT_CATEGORY_ID + "/" + INTELLIJ_TAG;
  private static final String COMMUNITY_NEW_TOPIC_URL = COMMUNITY_ROOT_URL + "new-topic"
    + "?title=Error in SonarLint for IntelliJ"
    + "&category_id=" + BUG_FAULT_CATEGORY_ID
    + "&tags=sonarlint," + INTELLIJ_TAG;

  @Override
  public String getReportActionText() {
    return "Report to SonarSource";
  }

  @Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
    @Nullable String additionalInfo,
    @NotNull Component parentComponent,
    @NotNull Consumer<SubmittedReportInfo> consumer) {
    StringBuilder description = new StringBuilder();
    description.append("Environment:\n");
    description.append("* Java version=").append(System.getProperty("java.version")).append("\n");
    description.append("* Java vendor=").append(System.getProperty("java.vendor")).append("\n");
    description.append("* OS name=").append(System.getProperty("os.name")).append("\n");
    description.append("* OS architecture=").append(System.getProperty("os.arch")).append("\n");
    description.append("* IDE=").append(getFullApplicationName()).append("\n");
    description.append("* SonarLint version=").append(SonarLintUtils.get(SonarApplication.class).getVersion()).append("\n");
    description.append("\n");
    if (additionalInfo != null) {
      description.append(additionalInfo);
      description.append("\n");
    }
    boolean somethingToReport = false;
    for (IdeaLoggingEvent ideaLoggingEvent : events) {
      final String message = ideaLoggingEvent.getMessage();
      if (StringUtils.isNotBlank(message)) {
        description.append(message).append("\n");
        somethingToReport = true;
      }
      final String throwableText = ideaLoggingEvent.getThrowableText();
      if (StringUtils.isNotBlank(throwableText)) {
        description.append("\n```\n");
        description.append(throwableText);
        description.append("\n```\n\n");
        somethingToReport = true;
      }
    }
    BrowserUtil.browse(getReportWithBodyUrl(description.toString()));
    if (somethingToReport) {
      consumer.consume(new SubmittedReportInfo(COMMUNITY_FAULT_CATEGORY_URL, "community support thread", SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
    }
    return somethingToReport;
  }

  protected String getFullApplicationName() {
    // TODO Replace by getFullApplicationName() when minimal version is 192.4787.16
    return ApplicationInfo.getInstance().getVersionName() + " " + ApplicationInfo.getInstance().getFullVersion();
  }

  String getReportWithBodyUrl(String description) {
    final String urlStart = COMMUNITY_NEW_TOPIC_URL + "&body=";
    final int charsLeft = MAX_URI_LENGTH - urlStart.length();

    return urlStart + getBoundedEncodedString(description, charsLeft);
  }

  String getBoundedEncodedString(String description, int maxLen) {
    try {
      String encoded = URLEncoder.encode(description, "UTF-8");
      while (encoded.length() > maxLen) {
        int lastNewline = description.lastIndexOf('\n');
        if (lastNewline == -1) {
          return "";
        }
        description = description.substring(0, lastNewline);
        encoded = URLEncoder.encode(description, "UTF-8");
      }

      return encoded;
    } catch (UnsupportedEncodingException e) {
      return "";
    }

  }
}
