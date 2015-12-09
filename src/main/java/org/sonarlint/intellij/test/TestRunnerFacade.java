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
package org.sonarlint.intellij.test;

import org.sonar.runner.api.IssueListener;
import org.sonarlint.intellij.analysis.SonarQubeRunnerFacade;

import java.util.Properties;

/**
 * To be used in tests, mocking sonar-runner
 */
public class TestRunnerFacade implements SonarQubeRunnerFacade {
  private static TestRunnerFacade instance;
  private SonarQubeRunnerFacade mock;

  private TestRunnerFacade() {
    // singleton
  }

  public static synchronized TestRunnerFacade getInstance() {
    if (instance == null) {
      instance = new TestRunnerFacade();
    }

    return instance;
  }

  public void setMocked(SonarQubeRunnerFacade mock) {
    this.mock = mock;
  }

  @Override
  public void startAnalysis(Properties props, IssueListener issueListener) {
    if (mock != null) {
      mock.startAnalysis(props, issueListener);
    }
  }

  @Override
  public void tryUpdate() {
    if (mock != null) {
      mock.tryUpdate();
    }
  }

  @Override
  public String getVersion() {
    if (mock != null) {
      return mock.getVersion();
    }
    return null;
  }

  @Override
  public void stop() {
    if (mock != null) {
      mock.stop();
    }
  }
}
