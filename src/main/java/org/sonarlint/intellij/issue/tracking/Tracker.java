/**
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
package org.sonarlint.intellij.issue.tracking;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Tracker<RAW extends Trackable, BASE extends Trackable> {

  public Tracking<RAW, BASE> track(Input<RAW> rawInput, Input<BASE> baseInput) {
    Tracking<RAW, BASE> tracking = new Tracking<>(rawInput, baseInput);

    // 1. match issues with same rule, same line and same line hash, but not necessarily with same message
    match(tracking, LineAndLineHashKeyFactory.INSTANCE);

    // 3. match issues with same rule, same message and same line hash
    match(tracking, LineHashAndMessageKeyFactory.INSTANCE);

    // 4. match issues with same rule, same line and same message
    match(tracking, LineAndMessageKeyFactory.INSTANCE);

    // 5. match issues with same rule and same line hash but different line and different message.
    // See SONAR-2812
    match(tracking, LineHashKeyFactory.INSTANCE);

    return tracking;
  }

  private void match(Tracking<RAW, BASE> tracking, SearchKeyFactory factory) {
    if (tracking.isComplete()) {
      return;
    }

    Map<SearchKey, List<BASE>> baseSearch = new HashMap<>();
    for (BASE base : tracking.getUnmatchedBases()) {
      SearchKey searchKey = factory.create(base);
      if (!baseSearch.containsKey(searchKey)) {
        baseSearch.put(searchKey, new ArrayList<>());
      }
      baseSearch.get(searchKey).add(base);
    }

    for (RAW raw : tracking.getUnmatchedRaws()) {
      SearchKey rawKey = factory.create(raw);
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

  private interface SearchKeyFactory {
    SearchKey create(Trackable trackable);
  }

  private static class LineAndLineHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer lineHash;
    private final Integer line;

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
    public SearchKey create(Trackable t) {
      return new LineAndLineHashKey(t);
    }
  }

  private static class LineHashAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final Integer lineHash;

    LineHashAndMessageKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.message = trackable.getMessage();
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
      LineHashAndMessageKey that = (LineHashAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(lineHash, that.lineHash)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      return result;
    }
  }

  private enum LineHashAndMessageKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey create(Trackable t) {
      return new LineHashAndMessageKey(t);
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
    public SearchKey create(Trackable t) {
      return new LineAndMessageKey(t);
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
    public SearchKey create(Trackable t) {
      return new LineHashKey(t);
    }
  }
}
