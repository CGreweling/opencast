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

package org.opencastproject.mergemediapackages.impl.endpoint;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.ws.rs.core.Response.status;
import static org.opencastproject.util.RestUtil.R.ok;
import static org.opencastproject.util.RestUtil.R.serverError;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;


import org.opencastproject.mergemediapackages.api.MergeMediapackagesService;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * A service endpoint to expose the {@link mergeMediapackagesService} via REST.
 */
@Path("/")
@RestService(name = "mergemediapackages", title = "mergemediapackages Service", abstractText = "Merge a List of Mediapackages to one and start a Workflow on the new one.", notes = {
        "No Notes" })
public class MergeMediapackagesServiceRestEndpoint {

  /**
   * The logger
   */
  private static final Logger logger = LoggerFactory.getLogger(MergeMediapackagesServiceRestEndpoint.class);

  private MergeMediapackagesService mergemediapackagesService;

  /**
   * Sets the mergemediapackages service
   *
   * @param mergemediapackagesService the mergemediapackages service
   */
  public void setmergemediapackagesService(MergeMediapackagesService mergemediapackagesService) {
    this.mergemediapackagesService = mergemediapackagesService;
  }

  @POST
  @Path("/")
  @Produces(MediaType.TEXT_XML)
  @RestQuery(name = "merge", description = "Merges a List of existing Mediapackages an starts a Workflow on the new created Mediapackage.", returnDescription = "The WorkflowInstance.",
          restParameters = {
          @RestParameter(name = "mediapackageIds", isRequired = true, description = "The Mediapackages to Merge."
                  + " The Id or multiple Ids as a comma seperated List ( IdOne,IdTwo )", type = STRING),
          @RestParameter(name = "workflowId", isRequired = true, description = "The workflowId(e.g. import)", type = STRING) }, reponses = {
          @RestResponse(responseCode = SC_OK, description = "The WorkflowInstance") })
  public Response mergemediapackages(@FormParam("mediapackageIds") String mediaPackageIds,
          @FormParam("workflowId") String workflowId) {

    try {
      return ok(mergemediapackagesService.mergemediapackages(Arrays.asList(mediaPackageIds.split(",")), workflowId));
    } catch (IllegalArgumentException e) {
      logger.debug("Unable to merge Mediapackages element: {}", e.getMessage());
      return status(Response.Status.BAD_REQUEST).build();
    } catch (Exception e) {
      logger.warn("Error merging mediapackages", e);
      return serverError();
    }
  }
}
