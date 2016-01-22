package mst.nsh9b3.uface;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class Authenticate extends IntentService
{
    // Current class description for log events
    private final String TAG = "uFace::Authenticate";

    // SharedPreferences used for sending the encrypted face
    private SharedPreferences sharedPref;

    // FTP client for transferring files between the phone and the server
    FTPClient ftpClient;

    // Handler to send messages to the user
    Handler handler;

    public Authenticate()
    {
        super("Authenticate");
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        // Get the savedFace location from the intent
        long nativeAddress = intent.getLongExtra(MainActivity.AUTHENTICATIONEXTRA, -1l);

        // Create a copy of the OpenCV Mat at the above location to avoid null pointer exceptions
        Mat faceToAuthenticate = new Mat(nativeAddress).clone();

        // Get the sharedPreferences
        sharedPref = this.getSharedPreferences(Options.SHARED_PREF, MODE_PRIVATE);

        // Create an ftp client for sending the file
        ftpClient = new FTPClient();

        // Get the server info from the sharedPreferences
        String[] serverInfo = getServerInformation();

        // If we can reach the server, continue with the operations
        if (canConnect(ftpClient, serverInfo))
        {
            Log.i(TAG, "Generating Histogram");
            // Use LBP algorithm to create feature vector of user's face
            JavaLBP LBP = new JavaLBP(faceToAuthenticate.getNativeObjAddr());

            Log.i(TAG, "Getting Histogram");
            // Get the generated histogram
            String[][] stringHist = LBP.getHistogram();

            Log.i(TAG, "Creating TimestampedID");
            // Get the timestampedID
            String[] timestampedID = getTimestampedID();

            Log.i(TAG, "Getting the public Key");
            // Get the encryption publicKey
            String[] publicKey = getPublicKey(ftpClient);

            // Encrypt the histogram and timestampedID
            String encryptedFilename = null;
            try
            {
                Log.i(TAG, "Encrypting");
                encryptedFilename = encryptHistogram(publicKey, timestampedID, stringHist);
                Log.i(TAG, "Done encrypting");

                // Send the file to the server for further processing
                Log.i(TAG, "Transferring file to Server");
                if (sendFileToServer(ftpClient, encryptedFilename))
                {
                    Log.i(TAG, "Transferred file to Server");
                    handler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(Authenticate.this, "FINISHED", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e)
            {
                Log.e(TAG, "Error: " + e);
            }
            ftpDisconnect(ftpClient);
        }
    }

    /**
     * Gets the server information from the sharedPreferences
     *
     * @return String[] containing the information to connect to the server: address, port, username, password
     */
    private String[] getServerInformation()
    {
        String[] serverInfo = new String[4];

        serverInfo[0] = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPAddress), "-1");
        serverInfo[1] = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPPort), "-1");
        serverInfo[2] = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPUsername), "-1");
        serverInfo[3] = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPPassword), "-1");

        return serverInfo;
    }

    /**
     * Attempt a connection with the provided information
     *
     * @param ftpClient  the FTP client used for connecting with the server
     * @param serverInfo the server's information containing address, port, username, and password
     * @return boolean as to whether or not a connection was made
     */
    private boolean canConnect(FTPClient ftpClient, String[] serverInfo)
    {
        boolean canConnect = false;

        try
        {
            Log.i(TAG, "connecting");
            // Try to connect with the provided server address and port
            ftpClient.connect(serverInfo[0], Integer.parseInt(serverInfo[1]));

            Log.i(TAG, "logging in");
            // Then try and login with the provided username and password
            if (ftpClient.login(serverInfo[2], serverInfo[3]))
            {
                Log.i(TAG, "logged in");
                // Successfully connected
                canConnect = true;
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(Authenticate.this, "Successfully connected to the server", Toast.LENGTH_SHORT).show();
                    }
                });
            } else
            {
                // Disconnect from the FTP client quietly
                try
                {
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (Exception ex)
                {

                }
            }
        } catch (Exception e)
        {
            // Could not connect or login properly
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(Authenticate.this, "Could not connect/login to server", Toast.LENGTH_LONG).show();
                }
            });

            // Disconnect from the FTP client quietly
            try
            {
                ftpClient.logout();
                ftpClient.disconnect();
            } catch (Exception ex)
            {

            }
        }

        return canConnect;
    }

    /**
     * Send a file from the Android phone to the FTP server
     * @param ftpClient information regarding the connected server
     * @param filename name of the file to be sent
     * @return boolean whether the file was successfully sent
     */
    private boolean sendFileToServer(FTPClient ftpClient, String filename)
    {
        try
        {
            InputStream input = new FileInputStream(filename);
            String name = filename.split("/")[filename.split("/").length - 1];
            name = "/ftp/".concat(name);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftpClient.storeFile(name, input);

            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply))
            {
                Log.e(TAG, "Apache Error - Reply Code: " + reply);

                ftpClient.logout();
                ftpClient.disconnect();

                return false;
            } else
            {
                Log.d(TAG, "Successfully transferred file");

                input.close();

                return true;
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);

            return false;
        }
    }

    /***
     * Disconnects from the FTP Server
     * @param ftpClient information regarding the connected server
     */
    private void ftpDisconnect(FTPClient ftpClient)
    {
        try
        {
            // Logout and then disconnect
            ftpClient.logout();
            ftpClient.disconnect();
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);
        }
    }


    private String[] getTimestampedID()
    {
        String[] timestampedID = new String[2];

        // Get unique ID
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();

        // Get timestamp
        String timestamp = "" + System.nanoTime();

        timestampedID[0] = tmDevice.concat(tmSerial);
        timestampedID[1] = timestamp;

        return timestampedID;
    }

    private String[] getPublicKey(FTPClient ftpClient)
    {
        String[] publicKey = null;

        try
        {
            OutputStream output = new FileOutputStream(this.getExternalCacheDir() + "/public_key.txt");
            ftpClient.retrieveFile("/ftp/public_key.txt", output);
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply))
            {
                Log.e(TAG, "Apache Error - Reply Code: " + reply);

                ftpClient.logout();
                ftpClient.disconnect();

                return null;
            } else
            {
                publicKey = readValuesInFile(this.getExternalCacheDir() + "/public_key.txt");
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);
        }

        return publicKey;
    }

    private String[] readValuesInFile(String filename)
    {
        String[] tokens = null;
        String line = null;

        BufferedReader reader;
        try
        {
            reader = new BufferedReader(new FileReader(filename));

            line = reader.readLine();
            tokens = line.split(" ");
        } catch (Exception e)
        {

        }

        return tokens;
    }

    /**
     * Encrypts the unique identifier and the histogram
     * @param publicKey  String[] containing the public key for Paillier encryption.
     *                   Index 0 is n, index 1 is g, index 2 is the bitlength
     * @param histogram String[] containing the histogram and unique ID
     * @return String for the name of the created encrypted file
     * @throws Exception
     */
    private String encryptHistogram(String[] publicKey, String[] timeStampedID, String[][] histogram) throws Exception
    {
        // Create a cryptosystem for encryption
        PaillierEncryption paillerCryptosystem =  new PaillierEncryption(publicKey[0], publicKey[1], publicKey[2]);

        // m = message, c = ciphertext
        BigInteger m;
        BigInteger[] c = new BigInteger[timeStampedID.length + histogram.length * histogram[0].length];

        Log.i(TAG, "Encrypting the timestampedID");
        // Encrypt the timestampedID values first
        for (int i = 0; i < timeStampedID.length; i++)
        {
            m = new BigInteger(timeStampedID[i]);
            c[i] = paillerCryptosystem.Encryption(m);
        }

        int index = timeStampedID.length;

        Log.i(TAG, "Encrypting the histogram");
        // Encrypt each value in the histogram
        for (int i = 0; i < histogram.length; i++)
        {
            for(int k = 0; k < histogram[i].length; k++)
            {
                m = new BigInteger(histogram[i][k]);
                c[index++] = paillerCryptosystem.Encryption(m);
            }
        }

        // Write the ciphertext to a File
        File outputDir = this.getExternalCacheDir(); // context being the Activity pointer
        File outputFile = null;
        try
        {
            outputFile = File.createTempFile("encryptedHistogram", ".txt", outputDir);
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);

            return null;
        }

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(outputFile.getAbsoluteFile()));
            for (int i = 0; i < c.length; i++)
            {
                writer.write(c[i] + " ");
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);
            return null;
        } finally
        {
            try
            {
                if (writer != null)
                {
                    writer.close();
                }
            } catch (IOException e)
            {
                Log.e(TAG, "Error: " + e);
            }
        }
        return outputFile.getAbsolutePath();
    }
}
