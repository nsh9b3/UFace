package mst.nsh9b3.uface;

import android.util.Log;

import org.opencv.core.Mat;

import java.util.HashMap;

/**
 * Created by nsh9b3 on 1/20/16.
 */
public class JavaLBP
{
    // Current class description for log events
    private static final String TAG = "uFace::JavaLBP";

    // Image used to generate the LBP histogram
    private Mat faceMat;

    // Keys for histogram containing uniform numbers only
    HashMap<Integer, Integer> histogramKeys;

    // Generated Histogram
    String[][] histogram;

    // Default values for simple LBP
    private final int radius = 1;
    private final int neighbors = 8;
    private static final int grid_size = 16;
    private static final int pixel_width_per_grid = TakePicture.IMAGEWIDTH / (grid_size / (int) Math.sqrt(grid_size));
    private static final int pixel_height_per_grid = TakePicture.IMAGEHEIGHT / (grid_size / (int) Math.sqrt(grid_size));

    // Number of bins for LBP
    private final int BINS = 59;

    //
    public static final int pixels_per_grid = pixel_height_per_grid * pixel_width_per_grid;
    public static final int max_bits_per_grid = (int) ((Math.log(pixels_per_grid)) / (Math.log(2)));
    public static final int concatenated_values = (int)Math.floor(PaillierEncryption.number_of_bits / max_bits_per_grid);

    public JavaLBP(long nativeFaceAddress)
    {
        // Grab the OpenCV matrix based on the provided memory location
        faceMat = new Mat(nativeFaceAddress);

        // Histogram containing the histogram of each individual element in the grid
        histogram = new String[grid_size][BINS];

        // Keys used to properly list uniform values
        histogramKeys = new HashMap<>();
        generateKeyMappings(histogramKeys);

        int index = 0;
        for (int i = 0; i < grid_size / (int) Math.sqrt(grid_size); i++)
        {
            // check if this pixel grid is at the top of the image
            boolean isTop = false;
            if (i == 0)
                isTop = true;

            // Get the column
            for (int k = 0; k < grid_size / (int) Math.sqrt(grid_size); k++)
            {
                // check if this pixel grid is at the left side of the image
                boolean isLeft = false;
                if (k == 0)
                    isLeft = true;

                // Generate the histogram for this section of the grid
                String[] histSec = generateLocalHistogram(i, k, isTop, isLeft);

                // Append this section's histogram to the main histogram
                histogram[index++] = histSec;
            }
        }
    }

    private String[] generateLocalHistogram(int row, int column, boolean atTop, boolean atLeft)
    {
        // This grids histogram
        int[] histSec = new int[BINS];
        for (int i = 0; i < histSec.length; i++)
        {
            histSec[i] = 0;
        }

        // Get grid dimensions
        int startRow = row * pixel_width_per_grid;
        int startCol = column * pixel_height_per_grid;
        int endRow = (row + 1) * pixel_width_per_grid - 1;
        int endCol = (column + 1) * pixel_height_per_grid - 1;

        // LBP needs to start 1 row lower to avoid seg faults
        int startLBPRow;
        if (atTop)
            startLBPRow = startRow + 1;
        else
            startLBPRow = startRow;

        // LBP needs to start 1 col to the right to avoid seg faults
        int startLBPCol;
        if (atLeft)
            startLBPCol = startCol + 1;
        else
            startLBPCol = startCol;

        // LBP algorithm
        for (int i = startLBPCol; i < endCol; i++)
        {
            for (int k = startLBPRow; k < endRow; k++)
            {
                int center = (int) faceMat.get(k, i)[0];

                // LBP values
                int topLeft = (int) faceMat.get(k - 1, i - 1)[0];
                int top = (int) faceMat.get(k - 1, i)[0];
                int topRight = (int) faceMat.get(k - 1, i + 1)[0];

                int midRight = (int) faceMat.get(k, i + 1)[0];

                int botRight = (int) faceMat.get(k + 1, i + 1)[0];
                int bot = (int) faceMat.get(k + 1, i)[0];
                int botLeft = (int) faceMat.get(k + 1, i - 1)[0];

                int midLeft = (int) faceMat.get(k, i - 1)[0];

                byte value = 0x00;
                if (topLeft >= center)
                    value |= 1 << 7;
                if (top >= center)
                    value |= 1 << 6;
                if (topRight >= center)
                    value |= 1 << 5;
                if (midRight >= center)
                    value |= 1 << 4;
                if (botRight >= center)
                    value |= 1 << 3;
                if (bot >= center)
                    value |= 1 << 2;
                if (botLeft >= center)
                    value |= 1 << 1;
                if (midLeft >= center)
                    value |= 1;

                // Place the value in the correct spot in the array based off the keys
                if (histogramKeys.get((value & 0xFF)) != null)
                    histSec[histogramKeys.get((value & 0xFF))]++;
                else
                    histSec[histSec.length - 1]++;
            }
        }

        convertToBytes(histSec);

        return convertToStrings(histSec);
    }

    private String[] convertToStrings(int[] intHist)
    {
        String[] stringHist = new String[intHist.length];
        for (int i = 0; i < stringHist.length; i++)
        {
            stringHist[i] = String.valueOf(intHist[i]);
        }

        return stringHist;
    }

    private void convertToBytes(int[] intHist)
    {

        for(int i = 0; i < intHist.length; i++)
        {
            byte[] byteHist = new byte[concatenated_values];
            byte first4 = 0x00;
            byte mid4 = 0x00;
            byte last4 = 0x00;
            int current;
            for(int k = 0; k < concatenated_values; k++)
            {

                if(k % 3 == 0)
                {
                    current = intHist[i++];
                    mid4 = (byte)(current >>> 4 & 0x0F);
                    byteHist[k++] = (byte)(first4|mid4);

                    last4 = (byte)(current & 0x0F);
                    current = intHist[i++];
                    first4 = (byte)(current >>> 4 & 0xF0);
                    byteHist[k++] = (byte)(last4 | first4);
                }
            }
        }
    }

    /**
     * Returns the histogram
     *
     * @return int[][] histogram[grid_size][59]
     */
    public String[][] getHistogram()
    {
        return histogram;
    }

    /**
     * Generates bins for the numbers below only. These are uniform values (less than 3 bitwise changes
     * in each value). All other values get dumped into a separate bin (non-uniform bin).
     * Only contains 1, 2, 3, 4, 6, 7, 8, 12, 14, 15, 16, 24, 28, 30, 31, 32, 48, 56, 60, 62, 63, 64,
     * 96, 112, 120, 124, 126, 127, 128, 129, 131, 135, 143, 159, 191, 192, 193, 195, 199, 207, 223,
     * 224, 225, 227, 231, 239, 240, 241, 243, 247, 248, 249, 251, 252, 253, 254, 255
     *
     * @param keys hashmap for these uniform values into the correct location in an array
     */
    private void generateKeyMappings(HashMap<Integer, Integer> keys)
    {
        int count = 0;
        for (int i = 0; i < 256; i++)
        {
            byte value = (byte) i;
            int transitions = 0;
            int last = value & 1;
            for (int k = 1; k < 8; k++)
            {
                if (((value >> k) & 1) != last)
                {
                    last = ((value >> k) & 1);
                    transitions++;
                    if (transitions > 2)
                    {
                        break;
                    }
                }
            }
            if (transitions <= 2)
            {
                keys.put(i, count++);
            }
        }
    }
}
