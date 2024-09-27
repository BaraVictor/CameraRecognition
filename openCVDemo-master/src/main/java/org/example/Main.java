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

  private static final double OBJECT_WIDTH_CM = 3.8;  // Width of the known rectangle in cm
  private static final double FOCAL_LENGTH = 700.0;   // Focal length calibrated for your camera

  public static void main(String[] args) {
    VideoCapture capture = new VideoCapture(0); // Use the default camera (index 0)

    if (!capture.isOpened()) {
      System.out.println("Error: Cannot open video capture.");
      return;
    }

    // Set up window to display video
    JFrame frame = new JFrame("Yellow Rectangle Detection in HSV");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JLabel label = new JLabel();
    frame.getContentPane().add(label, BorderLayout.CENTER);
    frame.setSize(1280, 720); // Window size
    frame.setVisible(true);

    Mat matFrame = new Mat();
    Mat hsvFrame = new Mat();
    Mat mask = new Mat();
    Mat coloredResult = new Mat(); // Mat for result

    // HSV range for yellow color
    Scalar lowerYellow = new Scalar(15, 100, 100);
    Scalar upperYellow = new Scalar(30, 255, 255);



    while (capture.read(matFrame)) {

      // Convert to HSV color space
      Imgproc.cvtColor(matFrame, hsvFrame, Imgproc.COLOR_BGR2HSV);

      // Create mask for yellow color
      Core.inRange(hsvFrame, lowerYellow, upperYellow, mask);
      /*
       * Mat result = new Mat(matFrame.size(), matFrame.type(), new Scalar(70, 70, 70));
       *
       */

      // Keep only the yellow areas in the original frame
      Core.bitwise_and(matFrame, matFrame, coloredResult, mask); // Apply the mask to keep only the yellow areas

      // Draw the contours and calculate angle and distance
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

      boolean rectangleFound = false;

      for (MatOfPoint contour : contours) {
        RotatedRect rotatedRect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));

        // Get the dimensions and angle
        Size rectSize = rotatedRect.size;
        double angle = rotatedRect.angle;

        // Check if the rectangle is valid (minimum dimensions)
        if (rectSize.width > 30 && rectSize.height > 30) {
          // Draw the rotated rectangle on the original frame for visualization
          Point[] vertices = new Point[4];
          rotatedRect.points(vertices);
          for (int i = 0; i < 4; i++) {
            Imgproc.line(matFrame, vertices[i], vertices[(i + 1) % 4], new Scalar(0, 255, 0), 2);
          }

          // Calculate distance
          double distance = (OBJECT_WIDTH_CM * FOCAL_LENGTH) / rectSize.width;

          // Display angle and distance above the rectangle
          String angleText = "Angle: " + String.format("%.2f", angle) + " degrees";
          String distanceText = "Distance: " + String.format("%.2f", distance) + " cm";

          // Use a position for the text just above the rectangle
          Point textPosition = new Point(vertices[0].x, vertices[0].y - 10);
          Imgproc.putText(matFrame, angleText, textPosition, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1);
          Imgproc.putText(matFrame, distanceText, new Point(textPosition.x, textPosition.y - 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1);

          rectangleFound = true; // Mark that at least one rectangle was found
        }
      }

      if (!rectangleFound) {
        Imgproc.putText(matFrame, "No Yellow Rectangle Detected", new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 255), 1); // Red color for no detection
      }

      // Convert Mat to BufferedImage for display
      BufferedImage img = matToBufferedImage(matFrame);
      label.setIcon(new ImageIcon(img));

      try {
        Thread.sleep(33);  // Approximately 30 FPS
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      if (!frame.isVisible()) {
        break;
      }
    }

    // Release resources
    capture.release();
    frame.dispose();
  }

  // Utility function to convert Mat to BufferedImage
  public static BufferedImage matToBufferedImage(Mat mat) {
    int type = (mat.channels() == 1) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
    BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
    byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    mat.get(0, 0, data);  // Copy data from Mat to BufferedImage
    return image;
  }
}
