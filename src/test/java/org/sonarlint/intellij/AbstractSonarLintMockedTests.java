/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij;

import com.intellij.mock.MockApplication;
import com.intellij.mock.MockComponentManager;
import com.intellij.mock.MockModule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.net.ssl.CertificateManager;
import org.junit.After;
import org.junit.Before;

import static org.mockito.Mockito.mock;

public abstract class AbstractSonarLintMockedTests {
  protected SonarLintMockProject project;
  protected MockModule module;
  protected MockApplication app;
  private Disposable disposable;

  @Before
  public final void prepareMocks() throws Exception {
    disposable = Disposer.newDisposable();
    app = new MockApplication(disposable);
    project = createProject();
    module = createModule();

    register(app, CertificateManager.class, new CertificateManager());
  }

  @After
  public final void dispose() throws Exception {
    project = null;
    module = null;
    // Restore original App
    Disposer.dispose(disposable);
  }

  private SonarLintMockProject createProject() {
    return new SonarLintMockProject(app.getPicoContainer(), disposable);
  }

  protected MockModule createModule() {
    var m = new MockModule(project, disposable);
    m.setName("testModule");
    return m;
  }

  protected SonarLintMockProject getProject() {
    return project;
  }

  protected <T> T register(Class<T> clazz) {
    T t = mock(clazz);
    register(clazz, t);
    return t;
  }

  protected <T> T register(MockComponentManager comp, Class<T> clazz) {
    T t = mock(clazz);
    register(comp, clazz, t);
    return t;
  }

  protected <T> void register(Class<T> clazz, T instance) {
    register(project, clazz, instance);
  }

  protected <T> void register(MockComponentManager comp, Class<T> clazz, T instance) {
    comp.addComponent(clazz, instance);
  }

}
