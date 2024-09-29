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

import static org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT;
import static org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH;

public class Main {

  static {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  }

  private static final double OBJECT_WIDTH_CM = 3.8;  // Width of the known rectangle in cm
  private static final double FOCAL_LENGTH = 700.0;   // Focal length calibrated for your camera
  private static final double ENGAGED_THRESHOLD = 100.0; // Define your threshold distance here

  public static void main(String[] args) {
    VideoCapture capture = new VideoCapture(0); // Use the default camera (index 0)

    if (!capture.isOpened()) {
      System.out.println("Error: Cannot open video capture.");
      return;
    }

    // Set up window to display video
    JFrame frame = new JFrame("Rectangle Detection in HSV");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JLabel label = new JLabel();
    frame.getContentPane().add(label, BorderLayout.CENTER);
    frame.setSize((int) capture.get(CAP_PROP_FRAME_WIDTH), (int) capture.get(CAP_PROP_FRAME_HEIGHT));
    frame.setLocationRelativeTo(null); // Center the window
    frame.setVisible(true);

    Mat matFrame = new Mat();
    Mat hsvFrame = new Mat();
    Mat maskRed = new Mat();
    Mat maskBlue = new Mat();
    Mat maskYellow = new Mat();
    Mat combinedMask = new Mat();
    Mat coloredResult = new Mat();

    // HSV ranges for red, blue, and yellow colors
    Scalar lowerRed1 = new Scalar(0, 100, 100);
    Scalar upperRed1 = new Scalar(10, 255, 255);
    Scalar lowerRed2 = new Scalar(170, 100, 100);
    Scalar upperRed2 = new Scalar(180, 255, 255);
    Scalar lowerBlue = new Scalar(100, 150, 0);
    Scalar upperBlue = new Scalar(140, 255, 255);
    Scalar lowerYellow = new Scalar(15, 100, 100);
    Scalar upperYellow = new Scalar(30, 255, 255);

    while (capture.read(matFrame)) {
      // Convert to HSV color space
      Imgproc.cvtColor(matFrame, hsvFrame, Imgproc.COLOR_BGR2HSV);

      // Create masks for red, blue, and yellow colors
      Core.inRange(hsvFrame, lowerRed1, upperRed1, maskRed);
      Core.inRange(hsvFrame, lowerRed2, upperRed2, maskRed);
      Core.inRange(hsvFrame, lowerBlue, upperBlue, maskBlue);
      Core.inRange(hsvFrame, lowerYellow, upperYellow, maskYellow);

      // Combine the masks
      Core.add(maskRed, maskBlue, combinedMask);
      Core.add(combinedMask, maskYellow, combinedMask);

      // Draw the contours and calculate angle and distance
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(combinedMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

      Point imageCenter = new Point(matFrame.width() / 2, matFrame.height() / 2);
      RotatedRect closestRect = null;
      double closestDistance = Double.MAX_VALUE;

      // Iterate over all contours to find the one closest to the center
      for (MatOfPoint contour : contours) {
        RotatedRect rotatedRect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));

        // Get dimensions and angle
        Size rectSize = rotatedRect.size;
        double angle = rotatedRect.angle;

        // Adjust angle based on width and height
        double adjustedAngle = (rectSize.width < rectSize.height) ? angle + 90 : angle;

        // Normalize angle between -90 and 90
        if (adjustedAngle > 90) {
          adjustedAngle -= 180;
        } else if (adjustedAngle < -90) {
          adjustedAngle += 180;
        }

        adjustedAngle = Math.max(-90, Math.min(90, adjustedAngle));

        // Check if rectangle is valid (minimum dimensions)
        if (rectSize.width > 30 && rectSize.height > 30) {
          // Draw the rotated rectangle on the original frame
          Point[] vertices = new Point[4];
          rotatedRect.points(vertices);
          for (int i = 0; i < 4; i++) {
            Imgproc.line(matFrame, vertices[i], vertices[(i + 1) % 4], new Scalar(0, 255, 0), 2); // Green for rectangle outline
          }

          // Determine color of rectangle based on the mask
          Scalar rectangleColor = new Scalar(0, 255, 0); // Default to green
          if (Core.countNonZero(maskRed) > 0) {
            rectangleColor = new Scalar(0, 0, 255); // Red
          } else if (Core.countNonZero(maskBlue) > 0) {
            rectangleColor = new Scalar(255, 0, 0); // Blue
          } else if (Core.countNonZero(maskYellow) > 0) {
            rectangleColor = new Scalar(0, 255, 255); // Yellow
          }

          // Draw orientation arrow in red
          Point center = rotatedRect.center;
          double arrowLength = 50;
          Point arrowEnd = new Point(
              center.x + arrowLength * Math.cos(Math.toRadians(adjustedAngle)),
              center.y + arrowLength * Math.sin(Math.toRadians(adjustedAngle))
          );
          Imgproc.arrowedLine(matFrame, center, arrowEnd, new Scalar(0, 0, 255), 2); // Red

          // Calculate distance
          double distance = (OBJECT_WIDTH_CM * FOCAL_LENGTH) / rectSize.width;

          // Display angle and distance
          int roundedAngle = (int) Math.round(adjustedAngle);
          String angleText = "Angle: " + roundedAngle + " decreeds";
          String distanceText = String.format("Distance: %.2f cm", distance);

          // Position text for display
          Point textPosition = new Point(vertices[0].x, vertices[0].y - 10);
          Imgproc.putText(matFrame, angleText, textPosition, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(75, 0, 130), 1); // Violet text
          Imgproc.putText(matFrame, distanceText, new Point(textPosition.x, textPosition.y - 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(75, 0, 130), 1); // Violet text

          // Check if the rectangle is the closest
          if (distance < closestDistance) {
            closestDistance = distance;
            closestRect = rotatedRect; // Save the closest rectangle
          }
        }
      }

      // If a closest rectangle was found, add the "Engaged" indicator
      if (closestRect != null) {
        // Calculate the distance for the closest rectangle
        double distance = (OBJECT_WIDTH_CM * FOCAL_LENGTH) / closestRect.size.width;

        // Use the angle of the closest rectangle for display
        double adjustedAngle = (closestRect.size.width < closestRect.size.height) ? closestRect.angle + 90 : closestRect.angle;

        // Normalize angle between -90 and 90
        if (adjustedAngle > 90) {
          adjustedAngle -= 180;
        } else if (adjustedAngle < -90) {
          adjustedAngle += 180;
        }

        adjustedAngle = Math.max(-90, Math.min(90, adjustedAngle));

        // Display angle and distance at the top of the frame
        int roundedAngle = (int) Math.round(adjustedAngle); // Use the adjusted angle for the engaged rectangle
        String angleText = "Angle: " + roundedAngle + " decreeds";
        String distanceText = String.format("Distance: %.2f cm", distance);

        // Position text for display in the upper left corner
        Point textPosition = new Point(10, 30); // Change to upper left
        Imgproc.putText(matFrame, angleText, textPosition, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(238, 130, 238), 1); // Light violet for angle
        Imgproc.putText(matFrame, distanceText, new Point(textPosition.x, textPosition.y + 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(238, 130, 238), 1); // Light violet for distance

        // Add the "Engaged" indicator below the angle and distance
        String engagedText = "Engaged: " + (closestDistance < ENGAGED_THRESHOLD ? "Yes" : "No");
        Imgproc.putText(matFrame, engagedText, new Point(textPosition.x, textPosition.y + 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 1); // Blue for "Engaged"
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
