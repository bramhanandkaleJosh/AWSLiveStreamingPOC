package mediaSources;

import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.util.ImageUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.concurrent.NotThreadSafe;
import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_TRACK_ID;

/**
 * Frame source backed by reading image files from webcam.
 */
@NotThreadSafe
public class CameraFrameSource {
    private static final String DELIMITER = "-";
    private static final int INFO_LENGTH = 4;
    private static final String VIDEO_TYPE = "video";
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final int fps;
    private final CameraMediaSourceConfiguration configuration;

    private OnStreamDataAvailable mkvDataAvailableCallback;
    private volatile boolean isRunning = false;
    private final Log log = LogFactory.getLog(CameraFrameSource.class);
    private long durationInMillis = 0;
    private int frameIndex = 0;
    private long frameStartMillis = 0;

    public CameraFrameSource(final CameraMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.fps = configuration.getFrameRate();
    }

    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Frame source is already running");
        }
        isRunning = true;
        startFrameGenerator();
    }

    public void stop() {
        isRunning = false;
        stopFrameGenerator();
    }

    public void onStreamDataAvailable(final OnStreamDataAvailable onMkvDataAvailable) {
        this.mkvDataAvailableCallback = onMkvDataAvailable;
    }

    private void startFrameGenerator() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    generateFrameAndNotifyListener();
                } catch (final KinesisVideoException e) {
                    log.error("Failed to keep generating frames with Exception", e);
                }
            }
        });
    }

    private void generateFrameAndNotifyListener() throws KinesisVideoException {
//        custom implementation for the frames from webcam to read for stream
        try {
            final Webcam webcam = Webcam.getDefault();
            Dimension dimension = new Dimension(WebcamResolution.VGA.getWidth(), WebcamResolution.VGA.getHeight());
            webcam.setViewSize(dimension);
            webcam.open();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            while (isRunning) {
                if (mkvDataAvailableCallback != null) {
                    frameIndex++;
                    ImageIO.write(webcam.getImage(), ImageUtils.FORMAT_JPG, outputStream);
                    mkvDataAvailableCallback.onFrameDataAvailable(createKinesisVideoFrameFromFile(webcam, outputStream)) ;
                }
                Thread.sleep(durationInMillis);
                outputStream.reset();
            }
            outputStream.close();
            webcam.close();
        } catch (final Exception e) {
            log.error("Frame interval wait interrupted by Exception ", e);
        }
    }

    private KinesisVideoFrame createKinesisVideoFrameFromFile(final Webcam webcam, ByteArrayOutputStream byteStream) {
        // fileName format: timecode-mediaType-isKeyFrame-frame, timecode is offset from beginning
        // 10000-audio-false-frame or 10999-video-true-frame
        final long startTime = System.currentTimeMillis();
        final long timestamp = startTime * HUNDREDS_OF_NANOS_IN_A_MILLISECOND
                + HUNDREDS_OF_NANOS_IN_A_MILLISECOND - frameStartMillis * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

        final long trackId = VIDEO_TRACK_ID ;
        final int isKeyFrame = FRAME_FLAG_KEY_FRAME;
        final byte[] bytes = byteStream.toByteArray();
//        Picture picture = Picture.create(WebcamResolution.VGA.getWidth(), WebcamResolution.VGA.getHeight(), ColorSpace.YUV420);
//        H264Encoder encoder = new H264Encoder(new DumbRateControl());
//        ByteBuffer byteBuffer = encoder.encodePFrame(picture, ByteBuffer.wrap(bytes));
        return new KinesisVideoFrame(frameIndex, isKeyFrame, timestamp, timestamp, 0,
                ByteBuffer.wrap(bytes) , trackId);

    }

    private void stopFrameGenerator() {

    }
}
