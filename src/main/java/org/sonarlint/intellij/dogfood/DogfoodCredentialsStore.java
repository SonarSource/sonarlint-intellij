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
package org.sonarlint.intellij.dogfood;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(
  name = "SonarLintDogfoodCredentials",
  storages = {@Storage("sonarlint-dogfood.xml")})
public final class DogfoodCredentialsStore implements PersistentStateComponent<DogfoodCredentials> {

  private DogfoodCredentials credentials = new DogfoodCredentials();

  public void save(DogfoodCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public DogfoodCredentials getState() {
    return credentials;
  }

  @Override
  public void loadState(@NotNull DogfoodCredentials state) {
    this.credentials = state;
  }

}
