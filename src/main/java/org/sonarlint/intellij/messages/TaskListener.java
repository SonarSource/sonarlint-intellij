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
package org.sonarlint.intellij.messages;

import com.intellij.util.messages.Topic;
import org.sonarlint.intellij.analysis.SonarLintJob;

/**
 * Notifies about analysis tasks starting and ending. It will be called for any analysis task, regardless of the trigger, if it is brackground or not, etc.
 */
public interface TaskListener {
  Topic<TaskListener> SONARLINT_TASK_TOPIC = Topic.create("SonarLint task start and finish", TaskListener.class);

  void started(SonarLintJob job);

  void ended(SonarLintJob job);

  abstract class Adapter implements TaskListener {
    @Override
    public void started(SonarLintJob job) {
      // can be optionally implemented
    }

    @Override
    public void ended(SonarLintJob job) {
      // can be optionally implemented
    }
  }
}
