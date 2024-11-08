/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import java.util.Collection;
import org.sonarlint.intellij.trigger.TriggerType;

/**
 * Notifies about analysis tasks starting. It will be called for any analysis task, regardless of the trigger, if it is background or not, etc.
 */
public interface AnalysisListener {
  Topic<AnalysisListener> TOPIC = Topic.create("SonarQube for IntelliJ Analysis Start", AnalysisListener.class);

  void started(Collection<VirtualFile> files, TriggerType trigger);

  abstract class Adapter implements AnalysisListener {
    @Override
    public void started(Collection<VirtualFile> files, TriggerType trigger) {
      // can be optionally implemented
    }
  }
}
