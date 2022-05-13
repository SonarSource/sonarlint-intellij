/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarlint.intellij.core;

public class Condition {
  private final String metricKey;
  private final String operator;
  private final String threshold;

  public Condition(String metricKey, String operator, String threshold) {
    this.metricKey = metricKey;
    this.operator = operator;
    this.threshold = threshold;
  }

  public String getMetricKey() {
    return metricKey;
  }

  public String getOperator() {
    return operator;
  }

  public String getThreshold() {
    return threshold;
  }
}
