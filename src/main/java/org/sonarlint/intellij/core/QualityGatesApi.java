/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.intellij.core;

import java.io.IOException;
import java.util.stream.Collectors;
import org.sonarqube.ws.Qualitygates;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class QualityGatesApi {
  private final ServerApiHelper helper;

  public QualityGatesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public String getId(String projectKey) {
    return convert(helper.get("/api/qualitygates/get_by_project.protobuf?project=" + urlEncode(projectKey))).getQualityGate().getId();
  }

  public QualityGate getQualityGate(String id) {
    var wsQg = convertQG(helper.get("/api/qualitygates/show.protobuf?id=" + urlEncode(id)));
    return new QualityGate(wsQg.getConditionsList().stream().map(c -> new Condition(c.getMetric(), c.getOp(), c.getError())).collect(Collectors.toList()));
  }

  private static Qualitygates.GetByProjectResponse convert(HttpClient.Response response) {
    try (var toBeClosed = response; var is = toBeClosed.bodyAsStream()) {
      return Qualitygates.GetByProjectResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load QG", e);
    }
  }

  private static Qualitygates.ShowWsResponse convertQG(HttpClient.Response response) {
    try (var toBeClosed = response; var is = toBeClosed.bodyAsStream()) {
      return Qualitygates.ShowWsResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load QG", e);
    }
  }
}
