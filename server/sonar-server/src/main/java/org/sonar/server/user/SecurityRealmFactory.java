/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.user;

import org.apache.commons.lang.StringUtils;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.ServerComponent;
import org.sonar.api.config.Settings;
import org.sonar.api.security.LoginPasswordAuthenticator;
import org.sonar.api.security.SecurityRealm;
import org.sonar.api.utils.SonarException;

/**
 * @since 2.14
 */
public class SecurityRealmFactory implements ServerComponent, Startable {

  private final boolean ignoreStartupFailure;
  private final SecurityRealm realm;

  public SecurityRealmFactory(Settings settings, SecurityRealm[] realms, LoginPasswordAuthenticator[] authenticators) {
    ignoreStartupFailure = settings.getBoolean(CoreProperties.CORE_AUTHENTICATOR_IGNORE_STARTUP_FAILURE);
    String realmName = settings.getString(CoreProperties.CORE_AUTHENTICATOR_REALM);
    String className = settings.getString(CoreProperties.CORE_AUTHENTICATOR_CLASS);
    SecurityRealm selectedRealm = null;
    if (!StringUtils.isEmpty(realmName)) {
      selectedRealm = selectRealm(realms, realmName);
      if (selectedRealm == null) {
        throw new SonarException(String.format(
          "Realm '%s' not found. Please check the property '%s' in conf/sonar.properties", realmName, CoreProperties.CORE_AUTHENTICATOR_REALM));
      }
    }
    if (selectedRealm == null && !StringUtils.isEmpty(className)) {
      LoginPasswordAuthenticator authenticator = selectAuthenticator(authenticators, className);
      if (authenticator == null) {
        throw new SonarException(String.format(
          "Authenticator '%s' not found. Please check the property '%s' in conf/sonar.properties", className, CoreProperties.CORE_AUTHENTICATOR_CLASS));
      }
      selectedRealm = new CompatibilityRealm(authenticator);
    }
    realm = selectedRealm;
  }

  public SecurityRealmFactory(Settings settings, LoginPasswordAuthenticator[] authenticators) {
    this(settings, new SecurityRealm[0], authenticators);
  }

  public SecurityRealmFactory(Settings settings, SecurityRealm[] realms) {
    this(settings, realms, new LoginPasswordAuthenticator[0]);
  }

  public SecurityRealmFactory(Settings settings) {
    this(settings, new SecurityRealm[0], new LoginPasswordAuthenticator[0]);
  }

  @Override
  public void start() {
    if (realm != null) {
      Logger logger = LoggerFactory.getLogger("org.sonar.INFO");
      try {
        logger.info("Security realm: " + realm.getName());
        realm.init();
        logger.info("Security realm started");
      } catch (RuntimeException e) {
        if (ignoreStartupFailure) {
          logger.error("IGNORED - Security realm fails to start: " + e.getMessage());
        } else {
          throw new SonarException("Security realm fails to start: " + e.getMessage(), e);
        }
      }
    }
  }

  @Override
  public void stop() {
    // nothing
  }

  public SecurityRealm getRealm() {
    return realm;
  }

  private static SecurityRealm selectRealm(SecurityRealm[] realms, String realmName) {
    for (SecurityRealm realm : realms) {
      if (StringUtils.equals(realmName, realm.getName())) {
        return realm;
      }
    }
    return null;
  }

  private static LoginPasswordAuthenticator selectAuthenticator(LoginPasswordAuthenticator[] authenticators, String className) {
    for (LoginPasswordAuthenticator lpa : authenticators) {
      if (lpa.getClass().getName().equals(className)) {
        return lpa;
      }
    }
    return null;
  }

}
