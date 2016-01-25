package mst.nsh9b3.uface;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.opencv.core.Mat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class Authenticate extends IntentService
{
    // Current class description for log events
    private final String TAG = "uFace::Authenticate";

    // SharedPreferences used for sending the encrypted face
    private SharedPreferences sharedPref;

    // Handler to send messages to the user
    Handler handler;

    // FTP handles all communication between the server and the phone
    FTP ftp;

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

        // Get the server info from the sharedPreferences
        String[] serverInfo = getServerInformation();

        // FTP client for communicating with the server
        ftp = new FTP(this, serverInfo);

        // If we can reach the server, continue with the operations
        if (ftp.canConnect(false))
        {

            Log.i(TAG, "Generating Histogram");
            // Use LBP algorithm to create feature vector of user's face
            JavaLBP LBP = new JavaLBP(faceToAuthenticate.getNativeObjAddr());

            Log.i(TAG, "Getting Histogram");
            // Get the generated histogram
            byte[] byteHist = LBP.getByteHist();

            Log.i(TAG, "Creating TimestampedID");
            // Get the timestampedID
            String timestampedID = getTimestampedID();

            Log.i(TAG, "Getting the public Key");
            // Get the encryption publicKey
            String[] publicKey = ftp.getPublicKey();

            // FilePath for encrypted file
            String encryptedFilename = null;

            // FilePath for non-encrypted file (if used)
            String plaintextFilename = null;
            try
            {
                Log.i(TAG, "Encrypting");
                // Encrypt the the values using the public key
                encryptedFilename = encryptHistogram(publicKey, timestampedID, byteHist);

                // Send the file to the server for further processing
                Log.i(TAG, "Transferring file to Server");
                if (ftp.sendFileToServer(encryptedFilename))
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

                plaintextFilename = createPlaintextFile(timestampedID, byteHist);
                if (ftp.sendFileToServer(plaintextFilename))
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
            ftp.ftpDisconnect();
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

    private String getTimestampedID()
    {
        String timestampedID;

        // Get unique ID
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();

        // Get timestamp
        String timestamp = "" + System.nanoTime();

        timestampedID = tmDevice.concat(tmSerial).concat(timestamp);

        return timestampedID;
    }


    /**
     * Encrypts the unique identifier and the histogram
     *
     * @param publicKey String[] containing the public key for Paillier encryption.
     *                  Index 0 is n, index 1 is g, index 2 is the bitlength
     * @param histogram String[] containing the histogram and unique ID
     * @return String for the name of the created encrypted file
     * @throws Exception
     */
    private String encryptHistogram(String[] publicKey, String[] timeStampedID, String[][] histogram) throws Exception
    {
        // Create a cryptosystem for encryption
        PaillierEncryption paillerCryptosystem = new PaillierEncryption(publicKey[0], publicKey[1], publicKey[2]);

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
            for (int k = 0; k < histogram[i].length; k++)
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

    private String encryptHistogram(String[] publicKey, String timeStampedID, byte[] histogram) throws Exception
    {
        // Create a cryptosystem for encryption
        PaillierEncryption paillerCryptosystem = new PaillierEncryption(publicKey[0], publicKey[1], publicKey[2]);

        // m = message, c = ciphertext
        BigInteger m;
        BigInteger[] c = new BigInteger[histogram.length + 1];

        int index = 0;

        Log.i(TAG, "Encrypting the timestampedID");
        // Encrypt the timestampedID values first
        m = new BigInteger(timeStampedID);
        c[index++] = paillerCryptosystem.Encryption(m);

        Log.i(TAG, "Encrypting the histogram");
        // Encrypt each value in the histogram
        int maxIterations = histogram.length / PaillierEncryption.number_of_bits;
        for (int i = 0; i <= maxIterations; i++)
        {
            m = new BigInteger(Arrays.copyOfRange(histogram, 128 * i, 128 * (i + 1)));
            c[index++] = paillerCryptosystem.Encryption(m);
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

    private String createPlaintextFile(String[][] histogram)
    {
        // Write the plaintet to a File
        File outputDir = this.getExternalCacheDir(); // context being the Activity pointer
        File outputFile = null;
        try
        {
            outputFile = File.createTempFile("plaintextHistogram", ".txt", outputDir);
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);

            return null;
        }

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(outputFile.getAbsoluteFile()));
            for (int i = 0; i < histogram.length; i++)
            {
                for (int k = 0; k < histogram[i].length; k++)
                {
                    writer.write(histogram[i][k] + " ");
                }
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

    private String createPlaintextFile(String timestampedID, byte[] histogram)
    {
        // Write the plaintet to a File
        File outputDir = this.getExternalCacheDir(); // context being the Activity pointer
        File outputFile = null;
        try
        {
            outputFile = File.createTempFile("plaintextHistogram", ".txt", outputDir);
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);

            return null;
        }

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(outputFile.getAbsoluteFile()));
            writer.write(timestampedID + " ");
            for (int i = 0; i < histogram.length; i++)
            {
                writer.write(Integer.toBinaryString(histogram[i] & 0xff).replace(' ', '0') + " ");
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
