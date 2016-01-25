package mst.nsh9b3.uface;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity extends Activity
{
    // Current class description for log events
    private static final String TAG = "uface::MainActivity";

    // Face Viewer for main application
    private ImageView faceImage;

    // OpenCV's representation of the taken picture
    private Mat faceMat;

    // Name of the Extra data used during authentication
    public static final String AUTHENTICATIONEXTRA = "savedFace";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set a default Image for the ImageView
        faceImage = (ImageView) findViewById(R.id.uFace_ImageView);
        faceImage.setImageResource(R.mipmap.ic_launcher);

        // Set the Mat containing the image of the face to null initially
        faceMat = null;


        int testValue = 3254;
        Log.d(TAG, Integer.toBinaryString(testValue & 0xFFF).replace(' ', '0'));
        Log.d(TAG, Integer.toBinaryString((testValue >>> 8) ).replace(' ', '0') + "");
        Log.d(TAG, Integer.toBinaryString((testValue >>> 4) & 0x0f).replace(' ','0') + "");
        Log.d(TAG, Integer.toBinaryString(((testValue >>> 4) & 0xf0) | ((testValue >>> 4) & 0x0f)).replace(' ', '0') + "");

//        BigInteger test = new BigInteger(new byte[]{(byte)255, (byte)102});
////        for(int i = 0; i < 8; i++)
////            test = test.setBit(i);
//        byte[] solution = test.toByteArray();
//        for(int i = 0; i < solution.length; i++)
//        {
//            Log.d(TAG, "" + String.format("%8s", Integer.toBinaryString(solution[i] & 0xFF)).replace(' ', '0'));
//        }
    }

    /**
     * Sets up options to successfully transfer file to the server(s)
     * @param view the selected view
     */
    public void onClickOptions(View view)
    {
        // Start the Options activity
        Intent intent = new Intent(this, Options.class);
        startActivity(intent);
    }

    /**
     * Takes a picture of the user's face
     * @param view the selected view
     */
    public void onClickTakePicture(View view)
    {
        // Start the Camera activity
        Intent intent = new Intent(this, TakePicture.class);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(resultCode == RESULT_OK)
        {
            // Get the extras from the intent
            Bundle extras = data.getExtras();

            // Get the native address of the OpenCV Mat
            long nativeAddress = extras.getLong(TakePicture.INTENTEXTRA);
            if(nativeAddress != -1)
            {
                // Set the faceMat to be the same variable
                faceMat = new Mat(nativeAddress);

                // Update the ImageView to see the face used for authenticating
                Bitmap faceBitmap = Bitmap.createBitmap(faceMat.width(), faceMat.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(faceMat, faceBitmap);
                faceImage.setImageBitmap(faceBitmap);
            }
        }
    }


    /**
     * Generates the histogram and sends the file(s) to the proper server
     * @param view the selected view
     */
    public void onClickAuthenticate(View view)
    {
        if(faceMat != null)
        {
            // Start the Authentication service
            Intent intent = new Intent(this, Authenticate.class);
            intent.putExtra(AUTHENTICATIONEXTRA, faceMat.getNativeObjAddr());
            startService(intent);

            Toast.makeText(this, "Attempting to transfer encrypted file", Toast.LENGTH_SHORT).show();
        }
        else
        {
            Toast.makeText(this, "Take a picture first", Toast.LENGTH_SHORT).show();
        }
    }

}
