/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

public class TaskProgressMonitor implements ClientProgressMonitor {
  private final ProgressIndicator indicator;
  private final ProgressManager progressManager;
  private final Project project;
  private final Supplier<Boolean> cancelledFlag;

  public TaskProgressMonitor(ProgressIndicator indicator, @Nullable Project project) {
    this(indicator, ProgressManager.getInstance(), project, () -> false);
  }

  public TaskProgressMonitor(ProgressIndicator indicator, @Nullable Project project, Supplier<Boolean> cancelledFlag) {
    this(indicator, ProgressManager.getInstance(), project, cancelledFlag);
  }

  public TaskProgressMonitor(ProgressIndicator indicator, ProgressManager progressManager, @Nullable Project project, Supplier<Boolean> cancelledFlag) {
    this.indicator = indicator;
    this.progressManager = progressManager;
    this.project = project;
    this.cancelledFlag = cancelledFlag;
  }

  /**
   * Returns true if the task should be cancelled as soon as possible.
   */
  @Override
  public boolean isCanceled() {
    return cancelledFlag.get() || indicator.isCanceled() || (project != null && project.isDisposed()) || Thread.currentThread().isInterrupted();
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
   * Execute a section of code that can't be canceled
   */
  @Override
  public void executeNonCancelableSection(Runnable nonCancelable) {
    progressManager.executeNonCancelableSection(nonCancelable);
  }
}
