package com.amazonaws.kinesisvideo.internal.service;

import com.amazonaws.kinesisvideo.common.function.Consumer;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.producer.ProducerException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class AckConsumer implements Consumer<InputStream> {
    private static final long STOPPED_TIMEOUT_IN_MILLISECONDS = 15000;
    private static final int FOUR_KB = 4096;
    private static final String END_OF_STREAM_MSG = "0\r\n\r\n";
    private final KinesisVideoProducerStream stream;
    private InputStream ackStream = null;
    private final CountDownLatch stoppedLatch;
    private final Logger log;
    private final long uploadHandle;
    private volatile boolean closed = false;

    public AckConsumer(final long uploadHandle,
                       @Nonnull final KinesisVideoProducerStream stream,
                       @Nonnull final Logger log) {
        this.stream = Preconditions.checkNotNull(stream);
        this.uploadHandle = uploadHandle;
        this.log = Preconditions.checkNotNull(log);
        this.stoppedLatch = new CountDownLatch(1);
    }

    @Override
    public void accept(final @Nonnull InputStream inputStream) {
        ackStream = Preconditions.checkNotNull(inputStream);

        // Start a long running operation
        processAckInputStream();
    }

    @Nullable
    public InputStream getAckStream() {
        return ackStream;
    }

    private void processAckInputStream() {
        Preconditions.checkNotNull(stream);

        final byte[] buffer = new byte[FOUR_KB];
        int bytesRead;
        log.info("Starting ACK processing");
        try {
            while (!closed) {
                // This is a blocking operation
                bytesRead = ackStream.read(buffer);

                String bytesString = null;
                if (bytesRead > 0) {
                    bytesString = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                }

                // Check for end-of-stream and 0 before processing
                if (stream.getStreamHandle() == NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE
                        || bytesRead <= 0 || END_OF_STREAM_MSG.equals(bytesString)) {
                    // End-of-stream
                    log.debug("Received end-of-stream for ACKs.");
                    closed = true;
                } else if (bytesRead != 0) {
                    log.debug("Received ACK bits: {}", bytesString);
                    try {
                        stream.parseFragmentAck(uploadHandle, bytesString);
                    } catch (final ProducerException e) {
                        // Log the exception
                        log.error("Processing ACK threw an exception. Logging and continuing.", e);
                    }
                }
            }

            log.debug("Finished reading ACKs stream");
        } catch (final IOException e) {
            // Log and exit
            log.error(e);
        } finally {
            stoppedLatch.countDown();
        }
    }

    public void close() throws ProducerException {
        // Trigger stopping
        closed = true;

        // Close the stream
        try {
            if (ackStream != null) {
                ackStream.close();
            }
        } catch (final IOException e) {
            throw new ProducerException(e);
        }

        // Block until loop finished of timed out.
        try {
            if (!stoppedLatch.await(STOPPED_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                throw new ProducerException("ACK stream stopping time out", 0);
            }
        } catch (final InterruptedException e) {
            throw new ProducerException(e);
        }
    }
}