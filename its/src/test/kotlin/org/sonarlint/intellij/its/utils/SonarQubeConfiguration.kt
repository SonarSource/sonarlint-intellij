/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.its.utils

import com.sonar.orchestrator.config.Configuration

/**
 * Orchestrator configuration from the environment, except for the JDK that starts SonarQube.
 *
 * Tests run on the IDE's bundled JBR, which lacks modules that SonarQube's Elasticsearch needs
 * (e.g. jdk.jlink). The build passes a full JDK as `its.sonarqube.java.home` in that case.
 */
fun sonarQubeConfiguration(): Configuration {
    val builder = Configuration.builder().addEnvVariables().addSystemProperties()
    System.getProperty("its.sonarqube.java.home")?.let { builder.setProperty("java.home", it) }
    return builder.build()
}
