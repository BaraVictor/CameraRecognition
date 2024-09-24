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

  private static String angleText = "";
  private static String distanceText = "";
  private static long lastUpdateTime = 0;

  public static void main(String[] args) {
    VideoCapture capture = new VideoCapture(0); // Use the default camera (index 0)

    if (!capture.isOpened()) {
      System.out.println("Error: Cannot open video capture.");
      return;
    }

    // Set up window to display video
    JFrame frame = new JFrame("Yellow Rectangle Detection and Distance");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JLabel label = new JLabel();
    frame.getContentPane().add(label, BorderLayout.CENTER);
    frame.setSize(1280, 720); // Window size
    frame.setVisible(true);

    Mat matFrame = new Mat();
    Mat hsvFrame = new Mat();
    Mat mask = new Mat();
    Mat blurred = new Mat();
    Mat edged = new Mat();

    // HSV range for yellow color
    Scalar lowerYellow = new Scalar(20, 100, 100);
    Scalar upperYellow = new Scalar(30, 255, 255);

    while (capture.read(matFrame)) {
      // Convert to HSV color space
      Imgproc.cvtColor(matFrame, hsvFrame, Imgproc.COLOR_BGR2HSV);

      // Create mask for yellow color
      Core.inRange(hsvFrame, lowerYellow, upperYellow, mask);

      // Apply Gaussian Blur
      Imgproc.GaussianBlur(mask, blurred, new Size(5, 5), 0);

      // Apply Canny edge detection
      Imgproc.Canny(blurred, edged, 50, 150);

      // Find contours
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

      boolean rectangleFound = false;

      for (MatOfPoint contour : contours) {
        RotatedRect rotatedRect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));

        // Get the dimensions and angle
        Size rectSize = rotatedRect.size;
        double angle = rotatedRect.angle;

        // Check if the rectangle is valid (minimum dimensions)
        if (rectSize.width > 30 && rectSize.height > 30) {
          // Draw the rotated rectangle
          Point[] vertices = new Point[4];
          rotatedRect.points(vertices);
          for (int i = 0; i < 4; i++) {
            Imgproc.line(matFrame, vertices[i], vertices[(i + 1) % 4], new Scalar(0, 255, 0), 2);
          }

          // Calculate distance
          double distance = (OBJECT_WIDTH_CM * FOCAL_LENGTH) / rectSize.width;

          // Update texts and reset timer
          angleText = "Angle: " + String.format("%.2f", angle) + " degrees";
          distanceText = "Distance: " + String.format("%.2f", distance) + " cm";
          lastUpdateTime = System.currentTimeMillis();

          rectangleFound = true;
          break; // Process only one rectangle
        }
      }

      if (!rectangleFound) {
        angleText = "";
        distanceText = "No Yellow Rectangle Detected";
      }

      // Display angle and distance if within the delay time
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastUpdateTime <= 1000) { // Show for 1 second
        Imgproc.putText(matFrame, angleText, new Point(10, 50),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1);
        Imgproc.putText(matFrame, distanceText, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1);
      } else {
        angleText = "";
        distanceText = "";
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
