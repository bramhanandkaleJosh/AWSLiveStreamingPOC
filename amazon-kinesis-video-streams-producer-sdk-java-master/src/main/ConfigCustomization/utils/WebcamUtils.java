package utils;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.util.ImageUtils;
import org.jcodec.api.transcode.Transcoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WebcamUtils {

    public static void main(String[] args) {
        webcamStreaming();
    }

    public static byte[] getImageByteArrayFromWebCam(Webcam webcam){
        webcam.open();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(webcam.getImage(), ImageUtils.FORMAT_WBMP, baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        webcam.close();

        return baos.toByteArray();
    }

    public static void webcamStreaming(){
        try {

            Webcam.setAutoOpenMode(true);
            Webcam webcam = Webcam.getDefault();
//            Dimension dimension = new Dimension(WebcamResolution.VGA.getWidth(), WebcamResolution.VGA.getHeight());
//            webcam.setViewSize(dimension);
//        webcam.getImageBytes()

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(webcam.getImage(), ImageUtils.FORMAT_JPG, outputStream);


            Picture picture = Picture.create(WebcamResolution.VGA.getWidth(), WebcamResolution.VGA.getHeight(), ColorSpace.YUV420);
//            H264Encoder encoder = new H264Encoder(new DumbRateControl());
//            ByteBuffer byteBuffer = encoder.encodePFrame(picture, ByteBuffer.wrap(outputStream.toByteArray()));
            outputStream.close();


           Transcoder trsn = Transcoder.newTranscoder().create();
//           trsn.


        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
