package mst.nsh9b3.uface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class TakePicture extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener
{
    // Current class description for log events
    private static final String TAG = "uface::TakePicture";

    // Cascade Classifier File
    private File cascadeFile;

    // Native Classifier used for better performance
    private DetectionBasedTracker nativeDetector;

    // Front-facing camera integer value used in OpenCV
    private static final int FRONT_CAMERA = 1;

    // Color picture
    private Mat rgba;

    // Gray-scale picture
    private Mat gray;

    // Needed size of face compared to the rest of the image
    private float relativeFaceSize = 0.3f;

    //Color of square drawn around the user's face (r, g, b, a)
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);

    //OpenCV Java Camera View
    private JavaCameraView cameraView;

    // Face image drawn to screen
    Mat croppedFace;

    // Face image saved for authentication
    Mat savedFace;

    // Size of Rectangle around saved image
    Rect faceRect;

    // Number of pixels for in savedFace
    public static final int IMAGEWIDTH = 256;
    public static final int IMAGEHEIGHT = 256;

    // Name of Intent extra key for savedFace
    public static final String INTENTEXTRA = "savedFace";

    //Loads the cascade classifier file
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this)
    {
        @Override
        public void onManagerConnected(int status)
        {
            switch (status)
            {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.d(TAG, "OpenCV loaded successfully");
                    System.loadLibrary("face_detection");
                    try
                    {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(cascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1)
                        {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        nativeDetector = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e)
                    {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                }
                break;
                default:
                {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Make sure Screen is not turned off
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_picture);

        // Load the classifier
        loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

        // Setup the camera
        cameraView = (JavaCameraView) findViewById(R.id.uFace_TakePicture_JavaCameraView);
        cameraView.setCameraIndex(FRONT_CAMERA);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.enableView();
        cameraView.setOnTouchListener(this);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug())
        {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback);
        } else
        {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause()
    {
        Log.i(TAG, "onPause");

        super.onPause();
        if (cameraView != null)
            cameraView.disableView();
    }

    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");

        super.onDestroy();
        if (cameraView != null)
            cameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height)
    {
        // Initiate each Mat for viewing the picture
        gray = new Mat();
        rgba = new Mat();
    }

    @Override
    public void onCameraViewStopped()
    {
        // Release any used data
        gray.release();
        rgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {
        // Get the current image
        rgba = inputFrame.rgba();
        gray = inputFrame.gray();

        // Set the minimum face size needed for the native detector
        int height = gray.rows();
        int absoluteFaceSize = 0;
        if (Math.round(height * relativeFaceSize) > 0)
        {
            absoluteFaceSize = Math.round(height * relativeFaceSize);
        }
        nativeDetector.setMinFaceSize(absoluteFaceSize);

        // Detect the face in the image
        MatOfRect faces = new MatOfRect();
        if (nativeDetector != null)
            nativeDetector.detect(gray, faces);
        Rect[] facesArray = faces.toArray();

        // Draw the square around the face
        if (facesArray.length > 0)
        {
            Imgproc.rectangle(rgba, facesArray[0].tl(), facesArray[0].br(), FACE_RECT_COLOR, 1);

            // Save the current face image
            faceRect = facesArray[0];
            croppedFace = gray.submat(faceRect);
        }

        return rgba;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        // Make sure the camera has detected a face already
        if(croppedFace != null)
        {
            savedFace = croppedFace.clone();

            // Resize the rectangle around the face so it contains mostly just the face
            int newRowAmount = (int)(savedFace.rows() * 0.90);
            int newColAmount = (int)(savedFace.cols() * 0.70);

            int rowStart = (savedFace.rows() / 2) - (newRowAmount / 2);
            int rowEnd = (savedFace.rows() / 2) + (newRowAmount / 2);
            int colStart = (savedFace.cols() / 2) - (newColAmount / 2);
            int colEnd = (savedFace.cols() / 2) + (newColAmount / 2);

            savedFace = savedFace.submat(rowStart, rowEnd, colStart, colEnd);

            Imgproc.resize(savedFace, savedFace, new Size(IMAGEWIDTH, IMAGEHEIGHT));

            // Send message to the user letting him/her know the image is saved properly
            Toast.makeText(this, "Saved Image", Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    @Override
    public void onBackPressed()
    {
        // Create intent to be sent to MainActivity
        Intent exitIntent = new Intent();

        // Make sure there is data to be sent back
        if(savedFace != null)
            exitIntent.putExtra(INTENTEXTRA, savedFace.getNativeObjAddr());
        else
            exitIntent.putExtra(INTENTEXTRA, (long) -1);

        // Exit this activity
        setResult(RESULT_OK, exitIntent);
        finish();
    }
}
