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

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Tracking<RAW extends Trackable, BASE extends Trackable> {

  /**
   * Matched issues -> a raw issue is associated to a base issue
   */
  private final IdentityHashMap<RAW, BASE> rawToBase = new IdentityHashMap<>();
  private final IdentityHashMap<BASE, RAW> baseToRaw = new IdentityHashMap<>();

  private final Collection<RAW> raws;
  private final Collection<BASE> bases;

  public Tracking(Input<RAW> rawInput, Input<BASE> baseInput) {
    this.raws = rawInput.getIssues();
    this.bases = baseInput.getIssues();
  }

  /**
   * Returns an Iterable to be traversed when matching issues. That means
   * that the traversal does not fail if method {@link #match(Trackable, Trackable)}
   * is called.
   */
  public Iterable<RAW> getUnmatchedRaws() {
    List<RAW> result = new ArrayList<>();
    for (RAW r : raws) {
      if (!rawToBase.containsKey(r)) {
        result.add(r);
      }
    }
    return result;
  }

  public Map<RAW, BASE> getMatchedRaws() {
    return rawToBase;
  }

  public BASE baseFor(RAW raw) {
    return rawToBase.get(raw);
  }

  /**
   * The base issues that are not matched by a raw issue and that need to be closed.
   */
  public Iterable<BASE> getUnmatchedBases() {
    List<BASE> result = new ArrayList<>();
    for (BASE b : bases) {
      if (!baseToRaw.containsKey(b)) {
        result.add(b);
      }
    }
    return result;
  }

  boolean containsUnmatchedBase(BASE base) {
    return !baseToRaw.containsKey(base);
  }

  void match(RAW raw, BASE base) {
    rawToBase.put(raw, base);
    baseToRaw.put(base, raw);
  }

  boolean isComplete() {
    return rawToBase.size() == raws.size();
  }

}
