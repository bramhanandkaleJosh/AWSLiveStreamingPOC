package utils;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.util.ImageUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Practice {

    public static void main(String[] args) throws InterruptedException{
        try {

//            Webcam w = Webcam.getDefault();
//            w.setViewSize(WebcamResolution.VGA.getSize());
//
//            ServerSocket ss=new ServerSocket(6666);
//            Socket socket = ss.accept();
//            InputStream inputStreamReader = socket.getInputStream();


//            Picture picture = new Picture("https://introcs.cs.princeton.edu/java/stdlib/mandrill.jpg");
//            picture.show();
//
//

            final Webcam webcam = Webcam.getDefault();



            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(webcam.getImage(), ImageUtils.FORMAT_JPG, outputStream);




        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
