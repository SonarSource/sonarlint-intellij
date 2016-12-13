/*
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.issue.LiveIssue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class AnalysisCallbackAggregatorTest {
  private AnalysisCallbackAggregator wrapper;
  private AnalysisCallback callback;

  @Before
  public void setUp() {
    callback = mock(AnalysisCallback.class);
  }

  @Test
  public void stopOnError() {
    wrapper = new AnalysisCallbackAggregator(callback, 1);

    Exception e = new IllegalStateException();
    wrapper.onError(e);
    wrapper.onSuccess(issues());

    verify(callback).onError(e);
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void aggregateIssues() {
    wrapper = new AnalysisCallbackAggregator(callback, 1);
    Map<VirtualFile, Collection<LiveIssue>> i1 = issues();
    Map<VirtualFile, Collection<LiveIssue>> i2 = issues();

    wrapper.onSuccess(i1);
    wrapper.onSuccess(i2);

    Map<VirtualFile, Collection<LiveIssue>> i3 = new HashMap<>();
    i3.putAll(i1);
    i3.putAll(i2);

    verify(callback).onSuccess(i3);
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void noErrorAfterDone() {
    wrapper = new AnalysisCallbackAggregator(callback, 1);

    Exception e = new IllegalStateException();
    Map<VirtualFile, Collection<LiveIssue>> i1 = issues();
    wrapper.onSuccess(i1);
    wrapper.onError(e);

    verify(callback).onSuccess(i1);
    verifyNoMoreInteractions(callback);
  }

  private Map<VirtualFile, Collection<LiveIssue>> issues() {
    VirtualFile file = mock(VirtualFile.class);
    LiveIssue issue = mock(LiveIssue.class);
    return Collections.singletonMap(file, Collections.singleton(issue));
  }
}
