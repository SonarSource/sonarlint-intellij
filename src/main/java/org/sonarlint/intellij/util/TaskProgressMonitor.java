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
package org.sonarlint.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;

public class TaskProgressMonitor extends ProgressMonitor {
  private final ProgressIndicator indicator;

  public TaskProgressMonitor(ProgressIndicator indicator) {
    this.indicator = indicator;
  }

  /**
   * Returns true if the task should be cancelled as soon as possible.
   */
  @Override
  public boolean isCanceled() {
    return indicator.isCanceled();
  }

  /**
   * Handles a message regarding the current action
   */
  @Override
  public void setMessage(String msg) {
    indicator.setText(msg);
  }

  /**
   * Handles the approximate fraction of the task completed.
   *
   * @param fraction Number between 0.0f and 1.0f
   */
  @Override
  public void setFraction(float fraction) {
    indicator.setFraction(fraction);
  }

  /**
   * Handles whether the task in progress can determinate the fraction of its progress.
   * If not set, it should be assumed false
   */
  @Override
  public void setIndeterminate(boolean indeterminate) {
    indicator.setIndeterminate(indeterminate);
  }

  /**
   * Marks the section of the task as not cancelable
   */
  @Override
  public void startNonCancelableSection() {
    indicator.startNonCancelableSection();
  }

  /**
   * It's possible to cancel the task from now on
   */
  @Override
  public void finishNonCancelableSection() {
    indicator.finishNonCancelableSection();
  }
}
