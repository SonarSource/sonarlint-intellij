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
package org.sonarlint.intellij.ui.nodes;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.intellij.ui.ColoredTreeCellRenderer;

import java.util.concurrent.TimeUnit;

public class TimeNode extends AbstractNode {
  private Range range;
  private final String label;

  public TimeNode(long deltaTimeMin, long deltaTimeMax, String label) {
    this.range = Range.range(deltaTimeMin, BoundType.CLOSED, deltaTimeMax, BoundType.OPEN);
    this.label = label;
  }

  public static TimeNode createLastDay() {
    return new TimeNode(TimeUnit.DAYS.toMillis(0), TimeUnit.DAYS.toMillis(1), "today");
  }

  public static TimeNode createLastWeek() {
    return new TimeNode(TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(7), "Last week");
  }

  public static TimeNode createLastMonth() {
    return new TimeNode(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30), "Last month");
  }

  public Range getTimeRange() {
    return range;
  }

  @Override public void render(ColoredTreeCellRenderer renderer) {
    renderer.append(label);
  }

  public boolean belongs(long time) {
    long delta = System.currentTimeMillis() - time;
    return range.contains(delta);
  }
}
