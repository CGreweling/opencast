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

package org.opencastproject.workflow.handler.composer;

import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.composer.api.EncodingProfile;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationTagUtil;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SelectTracksWorkflowOperationHandler extends AbstractWorkflowOperationHandler {
  private static final Logger logger = LoggerFactory.getLogger(SelectTracksWorkflowOperationHandler.class);

  /** Name of the 'encode to video only work copy' encoding profile */
  private static final String PREPARE_VIDEO_ONLY_PROFILE = "video-only.work";

  /** Name of the muxing encoding profile */
  private static final String MUX_AV_PROFILE = "mux-av.work";

  /** The composer service */
  private ComposerService composerService = null;

  /** The local workspace */
  private Workspace workspace = null;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  private enum AudioMuxing {
    NONE("none"), FORCE("force"), DUPLICATE("duplicate");

    private final String value;

    AudioMuxing(final String value) {
      this.value = value;
    }

    static AudioMuxing fromConfigurationString(final String s) {
      for (final AudioMuxing audioMuxing : AudioMuxing.values()) {
        if (audioMuxing.value.equals(s)) {
          return audioMuxing;
        }
      }
      throw new IllegalArgumentException("invalid audio muxing parameter \"" + s + "\"");
    }
  }

  private static final String CONFIG_AUDIO_MUXING = "audio-muxing";

  private static final String CONFIG_FORCE_TARGET = "force-target";

  private static final String FORCE_TARGET_DEFAULT = "presenter";

  static {
    CONFIG_OPTIONS = new TreeMap<>();
    CONFIG_OPTIONS.put("source-flavor", "The \"flavor\" of the track to use as a video source input");
    CONFIG_OPTIONS.put("target-flavor", "The flavor to apply to the encoded file");
    CONFIG_OPTIONS.put("target-tags", "The tags to apply to the encoded file");
    CONFIG_OPTIONS.put(CONFIG_FORCE_TARGET,
            String.format("Target flavor type for the \"%s\" option \"%s\" (default %s)", CONFIG_AUDIO_MUXING,
                    AudioMuxing.FORCE, FORCE_TARGET_DEFAULT));
    CONFIG_OPTIONS.put(CONFIG_AUDIO_MUXING,
            String.format("Either \"%s\", \"%s\" or \"%s\" to specially mux audio streams", AudioMuxing.NONE.value,
                    AudioMuxing.DUPLICATE.value, AudioMuxing.FORCE));
  }

  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param composerService
   *          the local composer service
   */
  protected void setComposerService(final ComposerService composerService) {
    this.composerService = composerService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  public void setWorkspace(final Workspace workspace) {
    this.workspace = workspace;
  }

  private EncodingProfile getProfile(final String identifier) {
    final EncodingProfile profile = this.composerService.getProfile(identifier);
    if (profile == null) {
      throw new IllegalStateException(String.format("couldn't find encoding profile \"%s\"", identifier));
    }
    return profile;
  }

  private static Optional<String> getConfiguration(final WorkflowInstance instance, final String key) {
    return Optional.ofNullable(instance.getCurrentOperation().getConfiguration(key)).map(StringUtils::trimToNull);
  }

  private enum SubTrack {
    AUDIO, VIDEO
  }

  private static final class AugmentedTrack {
    private Track track;
    private final boolean hideAudio;
    private final boolean hideVideo;

    private AugmentedTrack(final Track track, final boolean hideAudio, final boolean hideVideo) {
      this.track = track;
      this.hideAudio = hideAudio;
      this.hideVideo = hideVideo;
    }

    boolean has(final SubTrack t) {
      if (t == SubTrack.AUDIO) {
        return hasAudio();
      } else {
        return hasVideo();
      }
    }

    void resetTrack(final Track t) {
      track = t;
    }

    boolean hide(final SubTrack t) {
      if (t == SubTrack.AUDIO) {
        return hideAudio;
      } else {
        return hideVideo;
      }
    }

    boolean hasAudio() {
      return track.hasAudio();
    }

    boolean hasVideo() {
      return track.hasVideo();
    }

    void setFlavorSubtype(final String subtype) {
      track.setFlavor(new MediaPackageElementFlavor(track.getFlavor().getType(), subtype));
    }

    String getFlavorType() {
      return track.getFlavor().getType();
    }
  }

  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, final JobContext context)
          throws WorkflowOperationException {
    try {
      return doStart(workflowInstance);
    } catch (final EncoderException | MediaPackageException | IOException | NotFoundException e) {
      throw new WorkflowOperationException(e);
    }
  }

  private WorkflowOperationResult doStart(final WorkflowInstance workflowInstance)
          throws WorkflowOperationException, EncoderException, MediaPackageException, NotFoundException, IOException {
    final MediaPackage mediaPackage = workflowInstance.getMediaPackage();

    final MediaPackageElementFlavor sourceFlavor = getConfiguration(workflowInstance, "source-flavor")
            .map(MediaPackageElementFlavor::parseFlavor)
            .orElseThrow(() -> new IllegalStateException("Source flavor must be specified"));

    final Track[] tracks = mediaPackage.getTracks(sourceFlavor);

    if (tracks.length == 0) {
      logger.info("No audio/video tracks with flavor '{}' found to prepare", sourceFlavor);
      return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE);
    }

    final List<AugmentedTrack> augmentedTracks = createAugmentedTracks(tracks, workflowInstance);

    long queueTime = 0L;
    // This function is formulated in a way so that it's hopefully compatible with an event with an arbitrary number of
    // tracks. However, many of the requirements were written with one or two tracks in mind. For example, below, we
    // test if "all tracks are non-hidden" or "exactly one track is non-hidden". This works for arbitrary tracks, of
    // course, but with more than two, we might want something better. Hopefully, this will be easy to improve later
    // on.

    // First case: We have only tracks with non-hidden video streams. So we keep them all and possibly cut away audio.
    if (allNonHidden(augmentedTracks, SubTrack.VIDEO)) {
      final Optional<AudioMuxing> audioMuxing = getConfiguration(workflowInstance, CONFIG_AUDIO_MUXING)
              .map(AudioMuxing::fromConfigurationString);
      // For both special options, we need to find out if we have exactly one audio track present
      final Optional<AugmentedTrack> singleAudioTrackOpt = findSingleAudioTrack(augmentedTracks);
      if (audioMuxing.map(m -> m == AudioMuxing.DUPLICATE).orElse(Boolean.FALSE) && singleAudioTrackOpt.isPresent()) {
        // Special option: If we have multiple video tracks, but only one audio track: copy this audio track to
        // all video tracks.
        final AugmentedTrack singleAudioTrack = singleAudioTrackOpt.get();
        for (final AugmentedTrack t : augmentedTracks) {
          if (t.track != singleAudioTrack.track) {
            final TrackJobResult jobResult = mux(t.track, singleAudioTrack.track, mediaPackage);
            t.track = jobResult.track;
            queueTime += jobResult.waitTime;
          }
        }
      } else if (audioMuxing.map(m -> m == AudioMuxing.FORCE).orElse(Boolean.FALSE) && singleAudioTrackOpt
              .isPresent()) {
        // Special option: if the only audio track we have selected is not in the video track of "force-target", we
        // copy it there (and remove the original audio track).
        final AugmentedTrack singleAudioTrack = singleAudioTrackOpt.get();
        final String forceTargetOpt = getConfiguration(workflowInstance, CONFIG_FORCE_TARGET)
                .orElse(FORCE_TARGET_DEFAULT);

        final Optional<AugmentedTrack> forceTargetTrackOpt = findTrackByFlavorType(augmentedTracks, forceTargetOpt);

        if (!forceTargetTrackOpt.isPresent()) {
          throw new IllegalStateException(
                  String.format("\"%s\" set to \"%s\", but target flavor \"%s\" not found!",
                          CONFIG_AUDIO_MUXING,
                          AudioMuxing.FORCE.value, forceTargetOpt));
        }

        final AugmentedTrack forceTargetTrack = forceTargetTrackOpt.get();

        if (singleAudioTrack.track != forceTargetTrack.track) {
          // Copy it over...
          final TrackJobResult muxResult = mux(forceTargetTrack.track, singleAudioTrack.track, mediaPackage);
          forceTargetTrack.track = muxResult.track;
          queueTime += muxResult.waitTime;

          // ...and remove the original
          final TrackJobResult hideAudioResult = hideAudio(singleAudioTrack.track, mediaPackage);
          singleAudioTrack.track = hideAudioResult.track;
          queueTime += hideAudioResult.waitTime;
        }
      } else {
        // No special options selected, or conditions for special options don't match.
        queueTime += muxMultipleVideoTracks(mediaPackage, augmentedTracks);
      }
    } else {
      // Second case: we have exactly one video track that is not hidden (hopefully, because for all other cases there
      // were no requirements given).
      queueTime += muxSingleVideoTrack(mediaPackage, augmentedTracks);

    }

    final MediaPackageElementFlavor targetTrackFlavor = MediaPackageElementFlavor.parseFlavor(StringUtils.trimToNull(
            getConfiguration(workflowInstance, "target-flavor")
                    .orElseThrow(() -> new IllegalStateException("Target flavor not specified"))));

    // Update Flavor
    augmentedTracks.forEach(t -> t.setFlavorSubtype(targetTrackFlavor.getSubtype()));

    // Update Tags here
    getConfiguration(workflowInstance, "target-tags").ifPresent(tags -> {
      final WorkflowOperationTagUtil.TagDiff tagDiff = WorkflowOperationTagUtil.createTagDiff(tags);
      augmentedTracks.forEach(t -> WorkflowOperationTagUtil.applyTagDiff(tagDiff, t.track));
    });

    return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE, queueTime);
  }

  private Optional<AugmentedTrack> findTrackByFlavorType(final Collection<AugmentedTrack> augmentedTracks,
          final String flavorType) {
    return augmentedTracks.stream().filter(augmentedTrack -> augmentedTrack.getFlavorType().equals(flavorType))
            .findAny();
  }

  private long muxSingleVideoTrack(final MediaPackage mediaPackage, final Collection<AugmentedTrack> augmentedTracks)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    long queueTime = 0L;

    // Otherwise, we have just one video track that's not hidden (because hopefully, the UI prevented all other
    // cases). We keep that, and mux in the audio from the other track.
    final AugmentedTrack nonHiddenVideo = findNonHidden(augmentedTracks, SubTrack.VIDEO)
            .orElseThrow(() -> new IllegalStateException("couldn't find a stream with non-hidden video"));
    // Implicit here is the assumption that there's just _one_ other audio stream. It's written so that
    // we can loosen this assumption later on.
    final Optional<AugmentedTrack> nonHiddenAudio = findNonHidden(augmentedTracks, SubTrack.AUDIO);

    // If there's just one non-hidden video stream, and that one has hidden audio, we have to cut that away, too.
    if (nonHiddenVideo.hasAudio() && nonHiddenVideo.hideAudio && (!nonHiddenAudio.isPresent()
            || nonHiddenAudio.get() == nonHiddenVideo)) {
      final TrackJobResult jobResult = hideAudio(nonHiddenVideo.track, mediaPackage);
      nonHiddenVideo.track = jobResult.track;
      queueTime += jobResult.waitTime;
    } else if (!nonHiddenAudio.isPresent() || nonHiddenAudio.get() == nonHiddenVideo) {
      // It could be the case that the non-hidden video stream is also the non-hidden audio stream. In that
      // case, we don't have to mux. But have to clone it.
      final Track clonedTrack = (Track) nonHiddenVideo.track.clone();
      clonedTrack.setIdentifier(null);
      mediaPackage.add(clonedTrack);
      nonHiddenVideo.resetTrack(clonedTrack);
    } else {
      // Otherwise, we mux!
      final TrackJobResult jobResult = mux(nonHiddenVideo.track, nonHiddenAudio.get().track, mediaPackage);
      nonHiddenVideo.track = jobResult.track;
      queueTime += jobResult.waitTime;
    }
    // ...and then throw away everything else.
    augmentedTracks.removeIf(t -> t.track != nonHiddenVideo.track);
    return queueTime;
  }

  private long muxMultipleVideoTracks(final MediaPackage mediaPackage, final Iterable<AugmentedTrack> augmentedTracks)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    long queueTime = 0L;
    for (final AugmentedTrack t : augmentedTracks) {
      if (t.hasAudio() && t.hideAudio) {
        // The flavor gets "nulled" in the process. Reverse that so we can treat all tracks equally.
        final MediaPackageElementFlavor previousFlavor = t.track.getFlavor();
        final TrackJobResult trackJobResult = hideAudio(t.track, mediaPackage);
        trackJobResult.track.setFlavor(previousFlavor);
        t.resetTrack(trackJobResult.track);
        queueTime += trackJobResult.waitTime;
      } else {
        // Even if we don't modify the track, we clone and re-add it to the MP (since it will be a new track with a
        // different flavor)
        final Track clonedTrack = (Track) t.track.clone();
        clonedTrack.setIdentifier(null);
        mediaPackage.add(clonedTrack);
        t.resetTrack(clonedTrack);
      }
    }
    return queueTime;
  }

  /**
   * Returns the single track that has audio, or an empty {@code Optional} if either more than one audio track exists, or none exists.
   * @param augmentedTracks List of tracks
   * @return See above.
   */
  private Optional<AugmentedTrack> findSingleAudioTrack(final Iterable<AugmentedTrack> augmentedTracks) {
    AugmentedTrack result = null;
    for (final AugmentedTrack augmentedTrack : augmentedTracks) {
      if (augmentedTrack.hasAudio() && !augmentedTrack.hideAudio) {
        // Already got an audio track? Aw, then there's more than one! :(
        if (result != null) {
          return Optional.empty();
        }
        result = augmentedTrack;
      }
    }
    return Optional.ofNullable(result);
  }

  private TrackJobResult mux(final Track videoTrack, final Track audioTrack, final MediaPackage mediaPackage)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    // Find the encoding profile
    final EncodingProfile profile = getProfile(MUX_AV_PROFILE);

    final Job job = composerService.mux(videoTrack, audioTrack, profile.getIdentifier());
    if (!waitForStatus(job).isSuccess()) {
      throw new WorkflowOperationException(
              String.format("Muxing video track %s and audio track %s failed", videoTrack, audioTrack));
    }
    final MediaPackageElementFlavor previousFlavor = videoTrack.getFlavor();
    final TrackJobResult trackJobResult = processJob(videoTrack, mediaPackage, job);
    trackJobResult.track.setFlavor(previousFlavor);
    return trackJobResult;
  }

  private static final class TrackJobResult {
    private final Track track;
    private final long waitTime;

    private TrackJobResult(final Track track, final long waitTime) {
      this.track = track;
      this.waitTime = waitTime;
    }
  }

  private TrackJobResult hideAudio(final Track track, final MediaPackage mediaPackage)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    // Find the encoding profile
    final EncodingProfile profile = getProfile(PREPARE_VIDEO_ONLY_PROFILE);
    logger.info("Encoding video only track {} to work version", track);
    final Job job = composerService.encode(track, profile.getIdentifier());
    if (!waitForStatus(job).isSuccess()) {
      throw new WorkflowOperationException(String.format("Rewriting container for video track %s failed", track));
    }
    final MediaPackageElementFlavor previousFlavor = track.getFlavor();
    final TrackJobResult trackJobResult = processJob(track, mediaPackage, job);
    trackJobResult.track.setFlavor(previousFlavor);
    return trackJobResult;
  }

  private TrackJobResult processJob(final Track track, final MediaPackage mediaPackage, final Job job)
          throws MediaPackageException, NotFoundException, IOException {
    final Track composedTrack = (Track) MediaPackageElementParser.getFromXml(job.getPayload());
    mediaPackage.add(composedTrack);
    final String fileName = getFileNameFromElements(track, composedTrack);

    // Note that the composed track must have an ID before being moved to the mediapackage in the working file
    // repository. This ID is generated when the track is added to the mediapackage. So the track must be added
    // to the mediapackage before attempting to move the file.
    composedTrack.setURI(workspace
            .moveTo(composedTrack.getURI(), mediaPackage.getIdentifier().toString(), composedTrack.getIdentifier(),
                    fileName));
    return new TrackJobResult(composedTrack, job.getQueueTime());
  }

  private Optional<AugmentedTrack> findNonHidden(final Collection<AugmentedTrack> augmentedTracks, final SubTrack st) {
    return augmentedTracks.stream().filter(t -> t.has(st) && !t.hide(st)).findAny();
  }

  private boolean allNonHidden(final Collection<AugmentedTrack> augmentedTracks,
          @SuppressWarnings("SameParameterValue") final SubTrack st) {
    return augmentedTracks.stream().noneMatch(t -> !t.has(st) || t.hide(st));
  }

  private static String constructHideProperty(final String s, final SubTrack st) {
    return "hide_" + s + "_" + st.toString().toLowerCase();
  }

  private boolean trackHidden(final WorkflowInstance instance, final String subtype, final SubTrack st) {
    final String hideProperty = instance.getConfiguration(constructHideProperty(subtype, st));
    return Boolean.parseBoolean(hideProperty);
  }

  private List<AugmentedTrack> createAugmentedTracks(final Track[] tracks, final WorkflowInstance instance) {
    return Arrays.stream(tracks).map(t -> {
      final boolean hideAudio = trackHidden(instance, t.getFlavor().getType(), SubTrack.AUDIO);
      final boolean hideVideo = trackHidden(instance, t.getFlavor().getType(), SubTrack.VIDEO);
      return new AugmentedTrack(t, hideAudio, hideVideo);
    }).collect(Collectors.toList());
  }
}