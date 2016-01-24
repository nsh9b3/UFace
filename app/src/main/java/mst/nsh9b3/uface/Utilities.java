package mst.nsh9b3.uface;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Created by nick on 1/23/16.
 */
public class Utilities
{
    public static String[] readValuesInFile(String filename)
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
}
