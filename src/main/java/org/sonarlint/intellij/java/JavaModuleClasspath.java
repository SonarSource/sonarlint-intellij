/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.java;

import com.intellij.openapi.module.Module;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;

public class JavaModuleClasspath {

  private final Set<Module> dependentModules = new LinkedHashSet<>();
  private final Set<Module> testDependentModules = new LinkedHashSet<>();
  private final Set<String> libraries = new LinkedHashSet<>();
  private final Set<String> testLibraries = new LinkedHashSet<>();
  private final Set<String> binaries = new LinkedHashSet<>();
  private final Set<String> testBinaries = new LinkedHashSet<>();
  private String jdkHome;

  public Set<Module> dependentModules() {
    return dependentModules;
  }

  public Set<Module> testDependentModules() {
    return testDependentModules;
  }

  public Set<String> libraries() {
    return libraries;
  }

  public Set<String> testLibraries() {
    return testLibraries;
  }

  public Set<String> binaries() {
    return binaries;
  }

  public Set<String> testBinaries() {
    return testBinaries;
  }

  public void setJdkHome(@Nullable String jdkHome) {
    this.jdkHome = jdkHome;
  }

  @CheckForNull
  public String getJdkHome() {
    return jdkHome;
  }
}
