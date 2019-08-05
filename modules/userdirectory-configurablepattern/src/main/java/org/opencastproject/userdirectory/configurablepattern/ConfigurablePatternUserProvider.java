/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.userdirectory.configurablepattern;

import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserProvider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 */
@Component(
    property = {
        "service.description=Pattern Based User Provider"
    },
    immediate = true,
    service = UserProvider.class
)
public class ConfigurablePatternUserProvider implements UserProvider {

  private static final Logger logger = LoggerFactory.getLogger(ConfigurablePatternUserProvider.class);

  // should be configurable
  private Pattern pattern = Pattern.compile("^lti:unios:.*$");

  private SecurityService securityService;

  private void activate() {
    logger.info("{} loaded", ConfigurablePatternUserProvider.class.getSimpleName());
  }

  @Reference
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  public String getName() {
    return ConfigurablePatternUserProvider.class.getSimpleName();
  }

  @Override
  public Iterator<User> getUsers() {
    return Collections.emptyIterator();
  }

  @Override
  public User loadUser(final String userName) {
    if (!pattern.matcher(userName).matches()) {
      return null;
    }
    final JaxbOrganization organization = JaxbOrganization.fromOrganization(securityService.getOrganization());
    final Set<JaxbRole> roles = new HashSet<>();
    roles.add(new JaxbRole(organization.getAnonymousRole(), organization));
    return new JaxbUser(userName, getName(), organization, roles);
  }

  @Override
  public long countUsers() {
    return 0;
  }

  @Override
  public String getOrganization() {
    return securityService.getOrganization().getId();
  }

  @Override
  public Iterator<User> findUsers(String query, int offset, int limit) {
    return Collections.emptyIterator();
  }

  @Override
  public Iterator<User> findUsers(Collection<String> userNames) {
    return Collections.emptyIterator();
  }

  @Override
  public void invalidate(String userName) {
    // nothing to do
  }
}
