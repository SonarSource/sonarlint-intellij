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

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.wsclient.Host;
import org.sonar.wsclient.Sonar;
import org.sonar.wsclient.SonarClient;
import org.sonar.wsclient.connectors.HttpClient4Connector;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

public final class WSClientFactory implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance(WSClientFactory.class);

  private WSClientFactory() {
  }

  public static WSClientFactory getInstance() {
    return com.intellij.openapi.application.ApplicationManager.getApplication().getComponent(WSClientFactory.class);
  }

  /**
   * Creates Sonar web service client facade, which uses proxy settings from IntelliJ and load server password from safe store.
   */
  public ISonarWSClientFacade getSonarClient(SonarQubeServer sonarServer) {
    return getSonarClient(sonarServer, true);
  }

  /**
   * Creates Sonar web service client facade, which uses proxy settings from IntelliJ and load server password from safe store.
   */
  public ISonarWSClientFacade getSonarClient(SonarQubeServer sonarServer, boolean loadPasswordFromStorage) {
    Host host;
    if (loadPasswordFromStorage) {
      try {
        sonarServer.setPassword(PasswordSafe.getInstance().getPassword(null, SonarQubeServer.class, sonarServer.getId()));
      } catch (PasswordSafeException e) {
        LOG.error("Unable to load password", e);
      }
    }
    if (sonarServer.hasCredentials()) {
      host = new Host(sonarServer.getUrl(), sonarServer.getUsername(), sonarServer.getPassword());
    } else {
      host = new Host(sonarServer.getUrl());
    }
    return new SonarWSClientFacade(create(host), sonarServer);
  }

  /**
   * Creates Sonar web service client, which uses proxy settings from IntelliJ.
   */
  private Sonar create(Host host) {
    HttpClient4Connector connector = new HttpClient4Connector(host);
    configureProxy(connector.getHttpClient(), host);
    return new Sonar(connector);
  }

  /**
   * Workaround for http://jira.codehaus.org/browse/SONAR-1586
   */
  private void configureProxy(DefaultHttpClient httpClient, Host server) {
    try {
      Proxy proxyData = getIntelliJProxyFor(server);
      if (proxyData != null) {
        InetSocketAddress address = (InetSocketAddress) proxyData.address();
        HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        LOG.debug("Proxy for [" + address.getHostName() + "] - [" + address + "]");
        HttpHost proxy = new HttpHost(address.getHostName(), address.getPort());
        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        if (proxySettings.PROXY_AUTHENTICATION) {
          httpClient.getCredentialsProvider().setCredentials(
              new AuthScope(address.getHostName(), address.getPort()),
              new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN, proxySettings.getPlainProxyPassword()));
        }
      } else {
        LOG.debug("No proxy for [" + server.getHost() + "]");
      }
    } catch (Exception e) {
      LOG.error("Unable to configure proxy for sonar-ws-client", e);
    }
  }

  private Proxy getIntelliJProxyFor(Host server) {
    List<Proxy> proxies;
    try {
      proxies = CommonProxy.getInstance().select(new URL(server.getHost()));
    } catch (MalformedURLException e) {
      LOG.error("Unable to configure proxy", e);
      return null;
    }
    for (Proxy proxy : proxies) {
      if (proxy.type() == Proxy.Type.HTTP) {
        return proxy;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "WSClientFactory";
  }

  @Override
  public void initComponent() {
    // Nothing to do
  }

  @Override
  public void disposeComponent() {
    // Nothing to do
  }

}
