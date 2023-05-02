/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package com.sonar.orchestrator;

import com.sonar.orchestrator.config.Configuration;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.container.SonarDistribution;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.Location;
import com.sonar.orchestrator.server.StartupLogWatcher;
import com.sonar.orchestrator.util.OrchestratorUtils;
import com.sonar.orchestrator.util.System2;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

public class OrchestratorBuilder {
  private final Configuration config;
  private final System2 system2;
  private final SonarDistribution distribution;
  private final Map<String, String> overriddenProperties;
  private StartupLogWatcher startupLogWatcher;

  OrchestratorBuilder(Configuration initialConfig) {
    this(initialConfig, System2.INSTANCE);
  }

  OrchestratorBuilder(Configuration initialConfig, System2 system2) {
    this.config = initialConfig;
    this.system2 = system2;
    this.distribution = new SonarDistribution();
    this.overriddenProperties = new HashMap();
  }

  public OrchestratorBuilder setZipFile(File zip) {
    OrchestratorUtils.checkArgument(zip.exists(), "SonarQube ZIP file does not exist: %s", new Object[]{zip.getAbsolutePath()});
    OrchestratorUtils.checkArgument(zip.isFile(), "SonarQube ZIP is not a file: %s", new Object[]{zip.getAbsolutePath()});
    return this.setZipLocation(FileLocation.of(zip));
  }

  public OrchestratorBuilder setZipLocation(Location zip) {
    this.distribution.setZipLocation((Location)Objects.requireNonNull(zip));
    return this;
  }

  public OrchestratorBuilder setSonarVersion(String s) {
    OrchestratorUtils.checkArgument(!OrchestratorUtils.isEmpty(s), "Empty SonarQube version", new Object[0]);
    this.distribution.setVersion(s);
    return this;
  }

  public OrchestratorBuilder setStartupLogWatcher(@Nullable StartupLogWatcher w) {
    this.startupLogWatcher = w;
    return this;
  }

  public OrchestratorBuilder addPlugin(Location location) {
    this.distribution.addPluginLocation((Location)Objects.requireNonNull(location));
    return this;
  }

  public OrchestratorBuilder addBundledPlugin(Location location) {
    this.distribution.addBundledPluginLocation((Location)Objects.requireNonNull(location));
    return this;
  }

  public OrchestratorBuilder setOrchestratorProperty(String key, @Nullable String value) {
    checkNotEmpty(key);
    this.overriddenProperties.put(key, value);
    return this;
  }

  public OrchestratorBuilder setServerProperty(String key, @Nullable String value) {
    checkNotEmpty(key);
    this.distribution.setServerProperty(key, value);
    return this;
  }

  public OrchestratorBuilder enableCeDebug() {
    this.failIfRunningOnCI();
    this.setServerProperty("sonar.ce.javaAdditionalOpts", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006");
    return this;
  }

  public OrchestratorBuilder enableWebDebug() {
    this.failIfRunningOnCI();
    this.setServerProperty("sonar.web.javaAdditionalOpts", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
    return this;
  }

  private void failIfRunningOnCI() {
    if ("true".equals(this.system2.getenv("CIRRUS_CI"))) {
      throw new IllegalStateException("Method shouldn't be called on CI");
    }
  }

  public OrchestratorBuilder emptySonarProperties() {
    this.distribution.setEmptySonarProperties(true);
    return this;
  }

  public OrchestratorBuilder defaultForceAuthentication() {
    this.distribution.setDefaultForceAuthentication(true);
    return this;
  }

  public OrchestratorBuilder defaultForceDefaultAdminCredentialsRedirect() {
    this.distribution.setForceDefaultAdminCredentialsRedirect(true);
    return this;
  }

  public OrchestratorBuilder useDefaultAdminCredentialsForBuilds(boolean defaultAdminCredentialsForBuilds) {
    this.distribution.useDefaultAdminCredentialsForBuilds(defaultAdminCredentialsForBuilds);
    return this;
  }

  public OrchestratorBuilder setEdition(Edition edition) {
    this.distribution.setEdition(edition);
    return this;
  }

  private static void checkNotEmpty(String key) {
    OrchestratorUtils.checkArgument(!OrchestratorUtils.isEmpty(key), "Empty property key", new Object[0]);
  }

  public OrchestratorBuilder restoreProfileAtStartup(Location profileBackup) {
    this.distribution.restoreProfileAtStartup(profileBackup);
    return this;
  }

  public OrchestratorBuilder activateLicense() {
    this.distribution.activateLicense();
    return this;
  }

  public OrchestratorBuilder keepBundledPlugins() {
    this.distribution.setKeepBundledPlugins(true);
    return this;
  }

  public OrchestratorExtension build() {
    OrchestratorUtils.checkState(this.distribution.getZipLocation().isPresent() ^ this.distribution.getVersion().isPresent(), "One, and only one, of methods setSonarVersion(String) or setZipFile(File) must be called", new Object[0]);
    Configuration.Builder configBuilder = Configuration.builder();
    Configuration finalConfig = configBuilder.addConfiguration(this.config).addMap(this.overriddenProperties).build();
    return new OrchestratorExtension(finalConfig, this.distribution, this.startupLogWatcher);
  }
}
