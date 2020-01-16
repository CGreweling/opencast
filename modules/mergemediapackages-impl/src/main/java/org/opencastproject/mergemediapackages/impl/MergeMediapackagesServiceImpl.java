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

package org.opencastproject.mergemediapackages.impl;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.ingest.api.IngestException;
import org.opencastproject.ingest.api.IngestService;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mergemediapackages.api.MergeMediapackagesService;
import org.opencastproject.workflow.api.WorkflowInstance;

import com.entwinemedia.fn.data.Opt;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;
import java.util.stream.Collectors;

public class MergeMediapackagesServiceImpl implements MergeMediapackagesService, ManagedService {

  private AssetManager assetmanger;
  private IngestService ingestService;

  public void setAssetManager(AssetManager assetManager) {
    this.assetmanger = assetManager;
  }

  public void setIngestService(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  @Override
  public WorkflowInstance mergemediapackages(List<String> mediapackageIds, String workflowId) {

    WorkflowInstance workflowInstance;

    //get Mediapackages from Assetmanager
    List<MediaPackage> mediaPackageList = mediapackageIds.stream().map(n -> assetmanger.getMediaPackage(n))
            .filter(Opt::isSome).map(Opt::get).collect(Collectors.toList());

    try {
      MediaPackage newMediapackage = ingestService.createMediaPackage();
      newMediapackage = mergeMediapackageList(newMediapackage, mediaPackageList);
      workflowInstance = ingestService.ingest(newMediapackage, workflowId);
      return workflowInstance;
    } catch (MediaPackageException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (IngestException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  private MediaPackage mergeMediapackageList(MediaPackage finalMediapackage, List<MediaPackage> mediaPackageList) {
    for (MediaPackage mediaPackage : mediaPackageList) {
      for (MediaPackageElement mediaPackageElement : mediaPackage.getElements()) {
        if (mediaPackageElement.getFlavor() != null) {
          if (finalMediapackage.getElementsByFlavor(mediaPackageElement.getFlavor()).length == 0) {
            finalMediapackage.add(mediaPackageElement);
          }
        }
      }

    }
    return finalMediapackage;
  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {

  }
}
