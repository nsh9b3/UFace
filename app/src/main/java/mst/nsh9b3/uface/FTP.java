package mst.nsh9b3.uface;

import android.content.Context;
import android.util.Log;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by nick on 1/23/16.
 */
public class FTP
{
    private static final String TAG = "uFace::FTP";

    private FTPClient ftpClient;

    private String[] serverInfo;

    private Context context;

    public FTP(Context context, String[] serverInfo)
    {
        this.context = context;
        this.serverInfo = serverInfo;
        ftpClient = new FTPClient();
    }

    /**
     * Attempt a connection with the provided information
     * @param disconnect boolean as to whether the connection should be kept or not
     * @return boolean as to whether or not a connection can be made
     */
    public boolean canConnect(boolean disconnect)
    {
        boolean canConnect = false;

        try
        {
            Log.i(TAG, "Trying to Connect to " + serverInfo[0] + ":" + serverInfo[1]);
            // Try to connect with the provided server address and port
            ftpClient.connect(serverInfo[0], Integer.parseInt(serverInfo[1]));

            Log.i(TAG, "Logging in as " + serverInfo[2]);
            // Then try and login with the provided username and password
            if (ftpClient.login(serverInfo[2], serverInfo[3]))
            {
                Log.i(TAG, "Logged in");
                // Successfully connected
                canConnect = true;
            }
        } catch (Exception e)
        {
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply))
            {
                Log.e(TAG, "Apache Error - Reply Code: " + reply);
            }
        } finally
        {
            if(disconnect)
                ftpDisconnect();
        }
        return canConnect;
    }

    /**
     * Send a file from the Android phone to the FTP server
     *
     * @param filename name of the file to be sent
     * @return boolean whether the file was successfully sent
     */
    public boolean sendFileToServer(String filename)
    {
        try
        {
            InputStream input = new FileInputStream(filename);
            String name = filename.split("/")[filename.split("/").length - 1];
            name = "".concat(name);
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(org.apache.commons.net.ftp.FTP.STREAM_TRANSFER_MODE);
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

    public String[] getPublicKey()
    {
        String[] publicKey = null;

        try
        {
            OutputStream output = new FileOutputStream(context.getExternalCacheDir() + "/public_key.txt");
            ftpClient.retrieveFile("public_key.txt", output);
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply))
            {
                Log.e(TAG, "Apache Error - Reply Code: " + reply);

                ftpClient.logout();
                ftpClient.disconnect();

                return null;
            } else
            {
                publicKey = Utilities.readValuesInFile(context.getExternalCacheDir() + "/public_key.txt");
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error: " + e);
        }

        return publicKey;
    }

    /***
     * Disconnects from the FTP Server
     */
    public void ftpDisconnect()
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
}
