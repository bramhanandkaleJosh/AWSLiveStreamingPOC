package applications;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.contants.DemoTrackInfos;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.AudioVideoFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.AudioVideoFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.regions.Regions;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.util.ImageUtils;
import mediaSources.CameraMediaSource;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.ABSOLUTE_TIMECODES;

public class VideoStreamApplication {

    // Use a different stream name when testing audio/video sample
    private static final String STREAM_NAME = "FaceAndObejctDetectionStream";
    private static final int FPS_25 = 25;
    private static final int RETENTION_ONE_HOUR = 1;

    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String FRAME_DIR = "src/main/resources/data/audio-video-frames";
    // CHECKSTYLE:SUPPRESS:LineLength
    // Need to get key frame configured properly so the output can be decoded. h264 files can be decoded using gstreamer plugin
    // gst-launch-1.0 rtspsrc location="YourRtspUri" short-header=TRUE protocols=tcp ! rtph264depay ! decodebin ! videorate ! videoscale ! vtenc_h264_hw allow-frame-reordering=FALSE max-keyframe-interval=25 bitrate=1024 realtime=TRUE ! video/x-h264,stream-format=avc,alignment=au,profile=baseline,width=640,height=480,framerate=1/25 ! multifilesink location=./frame-%03d.h264 index=1
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private VideoStreamApplication() {
        throw new UnsupportedOperationException();
    }

    public static void main(final String[] args) {
        try {
            final KinesisVideoClient kinesisVideoClient = KinesisVideoJavaClientFactory
                    .createKinesisVideoClient(
                            Regions.US_EAST_1,
                            new ProfileCredentialsProvider("ashvini"));

            // create a media source. this class produces the data and pushes it into
            // Kinesis Video Producer lower level components
            final MediaSource mediaSource = createCameraMediaSource();

            // Audio/Video sample is available for playback on HLS (Http Live Streaming)
//            final MediaSource mediaSource = createFileMediaSource();

            // register media source with Kinesis Video Client
            kinesisVideoClient.registerMediaSource(mediaSource);

            // start streaming
            mediaSource.start();
        } catch (final KinesisVideoException e) {
            throw new RuntimeException(e);
        }
    }

    private static MediaSource createCameraMediaSource() {

        Webcam webcam = Webcam.getDefault();

//        byte[] cpd = { 0x01, 0x42, 0x00, 0x20, (byte) 0xff, (byte) 0xe1, 0x00, 0x23, 0x27, 0x42, 0x00, 0x20,
//                (byte) 0x89, (byte) 0x8b, 0x60, 0x28, 0x02, (byte) 0xdd, (byte) 0x80, (byte) 0x9e, 0x00, 0x00,
//                0x4e, 0x20, 0x00, 0x0f, 0x42, 0x41, (byte) 0xc0, (byte) 0xc0, 0x01, 0x77, 0x00, 0x00, 0x5d,
//                (byte) 0xc1, 0x7b, (byte) 0xdf, 0x07, (byte) 0xc2, 0x21, 0x1b, (byte) 0x80, 0x01, 0x00, 0x04,
//                0x28, (byte) 0xce, 0x1f, 0x20 };

        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        final CameraMediaSourceConfiguration configuration =
                new CameraMediaSourceConfiguration.Builder()
                        .withFrameRate((int) webcam.getFPS())
                        .withCameraFacing(1)
                        .withCameraId("webcam")
                        .withIsEncoderHardwareAccelerated(false)
                        .withRetentionPeriodInHours(1)
                        .withEncodingMimeType(ImageUtils.FORMAT_WBMP)
                        .withNalAdaptationFlags(StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE)
                        .withCodecPrivateData(AVCC_EXTRA_DATA)
                        .build();

        final CameraMediaSource mediaSource = new CameraMediaSource(STREAM_NAME);
        mediaSource.setUpWebCam(webcam);
        mediaSource.configure(configuration);
        return mediaSource;
    }


    /**
     * Create a MediaSource based on local sample H.264 frames.
     *
     * @return a MediaSource backed by local H264 frame files
     */
    private static MediaSource createImageFileMediaSource() {
        final ImageFileMediaSourceConfiguration configuration =
                new ImageFileMediaSourceConfiguration.Builder()
                        .fps(FPS_25)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        //.contentType("video/hevc") // for h265
                        .build();
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource(STREAM_NAME);
        mediaSource.configure(configuration);

        return mediaSource;
    }

    /**
     * Create a MediaSource based on local sample H.264 frames and AAC frames.
     *
     * @return a MediaSource backed by local H264 and AAC frame files
     */
    private static MediaSource createFileMediaSource() {
        final AudioVideoFileMediaSourceConfiguration configuration =
                new AudioVideoFileMediaSourceConfiguration.AudioVideoBuilder()
                        .withDir(FRAME_DIR)
                        .withRetentionPeriodInHours(RETENTION_ONE_HOUR)
                        .withAbsoluteTimecode(ABSOLUTE_TIMECODES)
                        .withTrackInfoList(DemoTrackInfos.createTrackInfoList())
                        .build();
        final AudioVideoFileMediaSource mediaSource = new AudioVideoFileMediaSource(STREAM_NAME);
        mediaSource.configure(configuration);

        return mediaSource;
    }

}
