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
package org.sonarlint.intellij.mediumtests.fixtures;

import com.google.protobuf.Message;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.Assert;

public class MockServer {

  private MockWebServer server;
  protected final Map<String, MockResponse> responsesByPath = new HashMap<>();

  public void start() {
    server = new MockWebServer();
    responsesByPath.clear();
    final Dispatcher dispatcher = new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        if (responsesByPath.containsKey(request.getPath())) {
          return responsesByPath.get(request.getPath());
        }
        return new MockResponse().setResponseCode(404);
      }
    };
    server.setDispatcher(dispatcher);
    try {
      server.start();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start the mock web server", e);
    }
  }

  public void shutdown() {
    try {
      server.shutdown();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot stop the mock web server", e);
    }
  }

  public void addStringResponse(String path, String body) {
    responsesByPath.put(path, new MockResponse().setBody(body));
  }

  public void removeResponse(String path) {
    responsesByPath.remove(path);
  }

  public void addResponse(String path, MockResponse response) {
    responsesByPath.put(path, response);
  }

  public void addProtobufResponse(String path, Message m) {
    try (var b = new Buffer()) {
      m.writeTo(b.outputStream());
      responsesByPath.put(path, new MockResponse().setBody(b));
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }

  public int getRequestCount() {
    return server.getRequestCount();
  }

  public RecordedRequest takeRequest() {
    try {
      return server.takeRequest();
    } catch (InterruptedException e) {
      Assert.fail(e.getMessage());
      return null; // appeasing the compiler: this line will never be executed.
    }
  }

  public String url(String path) {
    return server.url(path).toString();
  }

  public void addResponseFromResource(String path, String responseResourcePath) {
    try (var b = new Buffer()) {
      responsesByPath.put(path, new MockResponse().setBody(b.readFrom(Objects.requireNonNull(MockServer.class.getResourceAsStream(responseResourcePath)))));
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
}