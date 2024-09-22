package org.example;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

public class Main {

  static {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  }

  // Dimensiunile obiectului galben de referință în cm (8.9 cm înălțime și 3.8 cm lățime)
  private static final double OBJECT_WIDTH_CM = 3.8;  // Lățimea cunoscută a dreptunghiului galben
  private static final double FOCAL_LENGTH = 700.0;   // Lungimea focală calibrată pentru camera ta

  public static void main(String[] args) {
    VideoCapture capture = new VideoCapture(0); // Folosește camera implicită (index 0)

    if (!capture.isOpened()) {
      System.out.println("Error: Cannot open video capture.");
      return;
    }

    // Setare fereastră pentru afișarea videoclipului
    JFrame frame = new JFrame("Yellow Rectangle Detection and Distance");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JLabel label = new JLabel();
    frame.getContentPane().add(label, BorderLayout.CENTER);
    frame.setSize(1280, 720); // Dimensiunea ferestrei
    frame.setVisible(true);

    Mat matFrame = new Mat();
    Mat hsvFrame = new Mat();
    Mat mask = new Mat();
    Mat blurred = new Mat();
    Mat edged = new Mat();

    // Interval HSV pentru culoarea galbenă (ajustat pentru dreptunghiul galben)
    Scalar lowerYellow = new Scalar(20, 100, 100);
    Scalar upperYellow = new Scalar(30, 255, 255);

    Rect lastRectangle = null;
    double lastDistance = 0;

    while (capture.read(matFrame)) {
      // Conversie în spațiul de culoare HSV
      Imgproc.cvtColor(matFrame, hsvFrame, Imgproc.COLOR_BGR2HSV);

      // Masca pentru culoarea galbenă
      Core.inRange(hsvFrame, lowerYellow, upperYellow, mask);

      // Aplicați Gaussian Blur pentru a reduce zgomotul
      Imgproc.GaussianBlur(mask, blurred, new Size(5, 5), 0);

      // Aplicați detectarea marginilor Canny
      Imgproc.Canny(blurred, edged, 50, 150);

      // Găsiți contururile
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

      boolean rectangleFound = false;

      for (MatOfPoint contour : contours) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        double perimeter = Imgproc.arcLength(contour2f, true);
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);

        if (approx.total() == 4) {  // Detectăm dreptunghiuri
          Rect rect = Imgproc.boundingRect(new MatOfPoint(approx.toArray()));

          if (rect.width > 30 && rect.height > 30) {  // Filtrare pentru zgomot (dimensiuni minime)
            Imgproc.rectangle(matFrame, new Point(rect.x, rect.y),
                new Point(rect.x + rect.width, rect.y + rect.height),
                new Scalar(0, 255, 0), 2);

            // Calcularea distanței pe baza lățimii detectate și dimensiunii cunoscute
            double distance = (OBJECT_WIDTH_CM * FOCAL_LENGTH) / rect.width;

            lastRectangle = rect;
            lastDistance = distance;

            rectangleFound = true;
            break;  // Procesăm doar un dreptunghi
          }
        }
      }

      if (rectangleFound) {
        // Afișează distanța pentru dreptunghiul detectat
        Imgproc.putText(matFrame, "Distance: " + String.format("%.2f", lastDistance) + " cm",
            new Point(10, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0), 2);
      } else if (lastRectangle != null) {
        Imgproc.rectangle(matFrame, new Point(lastRectangle.x, lastRectangle.y),
            new Point(lastRectangle.x + lastRectangle.width, lastRectangle.y + lastRectangle.height),
            new Scalar(0, 255, 0), 2);

        Imgproc.putText(matFrame, "Distance: " + String.format("%.2f", lastDistance) + " cm (Last)",
            new Point(10, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0), 2);
      } else {
        Imgproc.putText(matFrame, "No Yellow Rectangle Detected",
            new Point(10, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 0, 255), 2);
      }

      // Conversie Mat la BufferedImage pentru afișare
      BufferedImage img = matToBufferedImage(matFrame);
      label.setIcon(new ImageIcon(img));

      try {
        Thread.sleep(33);  // Aproximativ 30 FPS
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      if (!frame.isVisible()) {
        break;
      }
    }

    // Eliberarea resurselor
    capture.release();
    frame.dispose();
  }

  // Funcție utilitară pentru conversia Mat în BufferedImage
  public static BufferedImage matToBufferedImage(Mat mat) {
    int type = (mat.channels() == 1) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
    BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
    byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    mat.get(0, 0, data);  // Copierea datelor din Mat în BufferedImage
    return image;
  }
}
