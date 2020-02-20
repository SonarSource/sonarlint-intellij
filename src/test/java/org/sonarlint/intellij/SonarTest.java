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
package org.sonarlint.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.impl.MessageBusImpl;
import com.intellij.util.net.ssl.CertificateManager;
import java.lang.reflect.Modifier;
import org.junit.After;
import org.junit.Before;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class SonarTest {
  protected Project project = createProject();
  protected Module module = createModule();
  protected VirtualFile root;
  protected Application app = mock(Application.class);

  @Before
  public final void setUp() {
    ApplicationManager.setApplication(app, mock(Disposable.class));
    when(app.isUnitTestMode()).thenReturn(true);
    when(app.getMessageBus()).thenReturn(new MessageBusImpl.RootBus(this));
    when(app.isHeadlessEnvironment()).thenReturn(true);
    when(app.getMessageBus()).thenReturn(new MessageBusImpl.RootBus(this));

    Answer<?> computableArg = invocation -> ((Computable<?>) invocation.getArgument(0)).compute();
    doAnswer(computableArg).when(app).runReadAction(any(Computable.class));

    Answer<Void> runnableArg = invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    };
    doAnswer(runnableArg).when(app).runReadAction(any(Runnable.class));

    Answer<Void> runArg = invocation -> {
      ((ThrowableComputable) invocation.getArgument(0)).compute();
      return null;
    };
    doAnswer(runArg).when(app).runReadAction(any(ThrowableComputable.class));

    register(app, CertificateManager.class, new CertificateManager());
    createModuleRoot();
  }

  @After
  public final void tearDown() {
    project = null;
    module = null;
  }

  private Project createProject() {
    Project project = mock(Project.class);
    when(project.getMessageBus()).thenReturn(new MessageBusImpl.RootBus(this));
    when(project.isDisposed()).thenReturn(false);

    return project;
  }

  private void createModuleRoot() {
    ModuleRootManager moduleRootManager = mock(ModuleRootManager.class);
    root = mock(VirtualFile.class);
    when(root.getCanonicalPath()).thenReturn("/src");
    when(root.getPath()).thenReturn("/src");
    VirtualFile[] roots = {root};
    when(moduleRootManager.getContentRoots()).thenReturn(roots);
    register(module, ModuleRootManager.class, moduleRootManager);
  }

  protected Module createModule() {
    Module m = mock(Module.class);
    when(m.getName()).thenReturn("testModule");
    when(m.getProject()).thenReturn(project);
    return m;
  }

  protected Project getProject() {
    return project;
  }

  protected <T> T register(Class<T> clazz) {
    T t = mock(clazz);
    register(clazz, t);
    return t;
  }

  protected <T> T register(ComponentManager comp, Class<T> clazz) {
    T t = mock(clazz);
    register(comp, clazz, t);
    return t;
  }

  protected void register(Class<?> clazz, Object instance) {
    register(project, clazz, instance);
  }

  protected void register(ComponentManager comp, Class<?> clazz, Object instance) {
    doReturn(instance).when(comp).getComponent(clazz);
  }

  protected <T> void registerEP(final ExtensionPointName<T> extensionPointName, final Class<T> clazz) {
    ExtensionsArea area = Extensions.getRootArea();
    final String name = extensionPointName.getName();
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = clazz.isInterface() || (clazz.getModifiers() & Modifier.ABSTRACT) != 0 ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, clazz.getName(), kind);
    }
  }
}
