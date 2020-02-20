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
package org.sonarlint.intellij.issue.tracking;

import com.intellij.openapi.application.ApplicationManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarlint.intellij.util.SonarLintUtils;

public class Tracker<RAW extends Trackable, BASE extends Trackable> {

  public Tracking<RAW, BASE> track(Input<RAW> rawInput, Input<BASE> baseInput) {
    return ApplicationManager.getApplication().<Tracking<RAW, BASE>>runReadAction(() -> {
      Tracking<RAW, BASE> tracking = new Tracking<>(rawInput, baseInput);

      // 1. match issues with same rule, same line and same text range hash, but not necessarily with same message
      match(tracking, LineAndTextRangeHashKeyFactory.INSTANCE);

      // 2. match issues with same rule, same message and same text range hash
      match(tracking, TextRangeHashAndMessageKeyFactory.INSTANCE);

      // 3. match issues with same rule, same line and same message
      match(tracking, LineAndMessageKeyFactory.INSTANCE);

      // 4. match issues with same rule and same text range hash but different line and different message.
      // See SONAR-2812
      match(tracking, TextRangeHashKeyFactory.INSTANCE);

      // 5. match issues with same rule, same line and same line hash
      match(tracking, LineAndLineHashKeyFactory.INSTANCE);

      // 6. match issues with same rule and same same line hash
      match(tracking, LineHashKeyFactory.INSTANCE);

      // 7. match issues with same server issue key
      match(tracking, ServerIssueSearchKeyFactory.INSTANCE);

      return tracking;
    });
  }

  private void match(Tracking<RAW, BASE> tracking, SearchKeyFactory factory) {
    if (tracking.isComplete()) {
      return;
    }

    Map<SearchKey, List<BASE>> baseSearch = new HashMap<>();
    for (BASE base : tracking.getUnmatchedBases()) {
      SearchKey searchKey = factory.apply(base);
      if (!baseSearch.containsKey(searchKey)) {
        baseSearch.put(searchKey, new ArrayList<>());
      }
      baseSearch.get(searchKey).add(base);
    }

    for (RAW raw : tracking.getUnmatchedRaws()) {
      SearchKey rawKey = factory.apply(raw);
      Collection<BASE> bases = baseSearch.get(rawKey);
      if (bases != null && !bases.isEmpty()) {
        // TODO taking the first one. Could be improved if there are more than 2 issues on the same line.
        // Message could be checked to take the best one.
        BASE match = bases.iterator().next();
        tracking.match(raw, match);
        baseSearch.get(rawKey).remove(match);
      }
    }
  }

  private interface SearchKey {
  }

  @FunctionalInterface
  private interface SearchKeyFactory extends Function<Trackable, SearchKey> {
    @Override
    SearchKey apply(Trackable trackable);
  }

  private static class LineAndTextRangeHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer textRangeHash;
    private final Integer line;

    LineAndTextRangeHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.line = trackable.getLine();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndTextRangeHashKey that = (LineAndTextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndTextRangeHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndTextRangeHashKey(t);
    }
  }

  private static class LineAndLineHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer line;
    private final Integer lineHash;

    LineAndLineHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.line = trackable.getLine();
      this.lineHash = trackable.getLineHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndLineHashKey that = (LineAndLineHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndLineHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndLineHashKey(t);
    }
  }

  private static class LineHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer lineHash;

    LineHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.lineHash = trackable.getLineHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineHashKey that = (LineHashKey) o;
      // start with most discriminant field
      return Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      return result;
    }
  }

  private enum LineHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineHashKey(t);
    }
  }

  private static class TextRangeHashAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final Integer textRangeHash;

    TextRangeHashAndMessageKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.message = trackable.getMessage();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      TextRangeHashAndMessageKey that = (TextRangeHashAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private enum TextRangeHashAndMessageKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new TextRangeHashAndMessageKey(t);
    }
  }

  private static class LineAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final Integer line;

    LineAndMessageKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.message = trackable.getMessage();
      this.line = trackable.getLine();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndMessageKey that = (LineAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndMessageKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndMessageKey(t);
    }
  }

  private static class TextRangeHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer textRangeHash;

    TextRangeHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      TextRangeHashKey that = (TextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private enum TextRangeHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new TextRangeHashKey(t);
    }
  }

  private static class ServerIssueSearchKey implements SearchKey {
    private final String serverIssueKey;

    ServerIssueSearchKey(Trackable trackable) {
      serverIssueKey = trackable.getServerIssueKey();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ServerIssueSearchKey that = (ServerIssueSearchKey) o;

      return !SonarLintUtils.isBlank(serverIssueKey) && !SonarLintUtils.isBlank(that.serverIssueKey) && serverIssueKey.equals(that.serverIssueKey);
    }

    @Override
    public int hashCode() {
      return serverIssueKey != null ? serverIssueKey.hashCode() : 0;
    }
  }

  private enum ServerIssueSearchKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable trackable) {
      return new ServerIssueSearchKey(trackable);
    }
  }
}
