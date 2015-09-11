/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.wsclient;

import java.util.List;

public interface ISonarWSClientFacade {

  public static enum ConnectionTestResult {
    OK, CONNECT_ERROR, AUTHENTICATION_ERROR;
  }

  ConnectionTestResult testConnection();

  String getServerVersion();

  List<ISonarRemoteProject> listAllRemoteProjects();

  List<ISonarRemoteProject> searchRemoteProjects(String partialName);

  List<ISonarRemoteModule> getRemoteModules(ISonarRemoteProject project);

}
