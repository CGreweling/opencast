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

package org.opencastproject.workflow.handler.acl;

import static org.opencastproject.metadata.dublincore.DublinCore.PROPERTY_IDENTIFIER;
import static org.opencastproject.metadata.dublincore.DublinCore.PROPERTY_TITLE;

import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.EName;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreUtil;
import org.opencastproject.metadata.dublincore.DublinCores;
import org.opencastproject.metadata.dublincore.EncodingSchemeUtils;
import org.opencastproject.metadata.dublincore.Precision;
import org.opencastproject.security.api.AccessControlEntry;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AclScope;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;
import org.opencastproject.workspace.api.Workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * The workflow definition for handling "series" operations
 */
public class SetAclandSeriesWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /**
   * The logging facility
   */
  private static final Logger logger = LoggerFactory.getLogger(SetAclandSeriesWorkflowOperationHandler.class);

  /**
   * The workspace service.
   */
  private Workspace workspace = null;

  /**
   * The authorization service
   */
  private AuthorizationService authorizationService;

  /**
   * The authorization service
   */
  private SeriesService seriesService;

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param authorizationService the authorization service
   */
  protected void setAuthorizationService(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param seriesService the authorization service
   */
  protected void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   * JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running setting ACLS and Series by metadatafields");

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();

    String creator = "";
    String coverage = "";
    String course = "";

    Catalog[] catalog;

    logger.info("Before trying to find dublincore catalaog: ");
    catalog = mediaPackage.getCatalogs(MediaPackageElementFlavor.parseFlavor("dublincore/episode"));
    for (Catalog dubCatalog : catalog) {
      try {
        DublinCoreCatalog dcCatalog = DublinCoreUtil.loadDublinCore(workspace, dubCatalog);
        creator = dcCatalog.getFirst(new EName("http://purl.org/dc/terms/", "creator"));
        course = dcCatalog.getFirst(new EName("http://purl.org/dc/terms/", "course"));
        coverage = dcCatalog.getFirst(new EName("http://purl.org/dc/terms/", "coverage"));
        dcCatalog.set(DublinCoreCatalog.PROPERTY_IS_PART_OF, coverage);
      } catch (Exception e) {

      }

    }

    // get acl for episode
    AccessControlList acl;
    Tuple<AccessControlList, AclScope> existingACL = authorizationService.getAcl(mediaPackage, AclScope.Episode);
    if (existingACL.getA().getEntries().isEmpty()) {
      acl = new AccessControlList();
    } else {
      acl = existingACL.getA();
    }

    logger.info("Adding roles to ACL");
    acl.getEntries().add(new AccessControlEntry("ROLE_OAUTH_USER", "read", true));
    acl.getEntries().add(new AccessControlEntry(creator, "read", true));
    acl.getEntries().add(new AccessControlEntry(creator, "write", true));
    acl.getEntries().add(new AccessControlEntry(creator, "delete", true));

    try {
      authorizationService.setAcl(mediaPackage, AclScope.Episode, acl);
    } catch (MediaPackageException e) {
      e.printStackTrace();
    }

    try {
      //set the Series
      String seriesId = coverage;
      if (!existSeries(seriesId)) {
        //createSeries
        DublinCoreCatalog seriesDublincore = createSeries(coverage, course);
        seriesService.updateSeries(seriesDublincore);
        seriesService.updateAccessControl(seriesId, acl);
      }
      mediaPackage.setSeries(seriesId);

    } catch (Exception e) {
      e.printStackTrace();
    }

    return createResult(mediaPackage, Action.CONTINUE);
  }

  private DublinCoreCatalog createSeries(String coverage, String course) {

    DublinCoreCatalog dc = DublinCores.mkOpencastSeries().getCatalog();
    dc.set(PROPERTY_IDENTIFIER, coverage);
    dc.set(DublinCore.PROPERTY_CREATED, EncodingSchemeUtils.encodeDate(new Date(), Precision.Second));
    dc.set(PROPERTY_TITLE, course);
    return dc;
  }

  private boolean existSeries(String seriesID) {
    try {
      DublinCore seriesDublincore = seriesService.getSeries(seriesID);
    } catch (Exception e) {
      return false;
    }

    return true;
  }

}
