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
    int[][] histogram;
    byte[][] concatHist;
    byte[][] byteMatrix;

    // Default values for simple LBP
    private final int radius = 1;
    private final int neighbors = 8;
    private static final int grid_size = 16;
    private static final int pixel_width_per_grid = TakePicture.IMAGEWIDTH / (grid_size / (int) Math.sqrt(grid_size));
    private static final int pixel_height_per_grid = TakePicture.IMAGEHEIGHT / (grid_size / (int) Math.sqrt(grid_size));

    // Number of bins for LBP
    private final int BINS = 59;

    public JavaLBP(long nativeFaceAddress)
    {
        // Grab the OpenCV matrix based on the provided memory location
        faceMat = new Mat(nativeFaceAddress);

        // Histogram containing the histogram of each individual element in the grid
        histogram = new int[grid_size][BINS];

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
                int[] histSec = generateLocalHistogram(i, k, isTop, isLeft);

                // Append this section's histogram to the main histogram
                histogram[index++] = histSec;
            }
        }

        // Convert to bytes and combine into sections so that less encryptions occur
        int[] histValues = getIntArray(histogram);
//        convertToBytes(histValues);
        testToBytes(histValues);
    }

    private int[] getIntArray(int[][] histogram)
    {
        int[] histValues = new int[grid_size * BINS];
        int index = 0;
        for(int i = 0; i < histogram.length; i++)
        {
            for(int k = 0; k < histogram[i].length; k++)
            {
                histValues[index++] = histogram[i][k];
            }
        }

        return histValues;
    }

    private int[] generateLocalHistogram(int row, int column, boolean atTop, boolean atLeft)
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

        return histSec;
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
        concatHist = new byte[(int)Math.ceil(intHist.length / 85.0)][(int)Math.ceil(85*1.5)];
        boolean isDone = false;
        int iterations = 0;
        while(!isDone)
        {
            byte[] byteHist = new byte[(int)Math.ceil(85*1.5)];
            int index = 0;
            byte top = 0x00;
            byte bot;
            for (int i = 0; i < 85; i++)
            {
                int current;
                if((i+iterations*85) < intHist.length)
                    current = intHist[i+iterations*85];
                else
                {
                    isDone = true;
                    break;
                }

                if (i % 2 == 0)
                {
                    bot = (byte) (current >>> 8);
                    byteHist[index++] = (byte) ((top & 0xF0) | (bot & 0x0F));
                    top = (byte) current;
                    bot = (byte) current;
                    byteHist[index++] = (byte) ((top & 0xF0) | (bot & 0x0F));
                } else
                {
                    top = (byte) (current >>> 4);
                    bot = (byte) (current >>> 4);
                    byteHist[index++] = (byte) ((top & 0xF0) | (bot & 0x0F));
                    top = (byte) (current << 4);
                }
            }
            concatHist[iterations++] = byteHist;
            if(iterations == (int)Math.ceil(intHist.length / 85.0))
            {
                isDone = true;
            }
        }
//        BigInteger test = new BigInteger(Arrays.copyOfRange(byteHist, 0, 128));
    }

    private void testToBytes(int[] intHist)
    {
        // Number of pixels in entire grid
        int pixelsInGrid = TakePicture.IMAGEHEIGHT * TakePicture.IMAGEWIDTH;

        // Number of pixels in 1 section of the grid
        int pixelsPerSection = pixel_height_per_grid * pixel_width_per_grid;

        // Number of bits needed to represent section of the grid
        int maxBits = (int)(Math.log(pixelsPerSection)/ Math.log(2));

        // Number of bytes needed per array of the Matrix below (Cols)
        int bytesNeeded = PaillierEncryption.number_of_bits / 8;

        // Number of integers for every byte array
        float numInts = PaillierEncryption.number_of_bits / (float) maxBits;

        // Check if numInts has a decimal place
        if(numInts == (int) numInts)
        {
            numInts--;
        }

        // Create byte matrix
        byteMatrix = new byte[(int)Math.ceil(intHist.length / numInts)][bytesNeeded];

        // Create byte array (these are the arrays set in the matrix above
        byte[] byteArray = new byte[bytesNeeded];

        // This is the first bit position that needs to be updated in the byte array
        int bitPos = 0;

        // This is set to true if the first byte needs to be all zeroes (to avoid error in encryption)
        boolean firstEmpty = false;

        // This is the number of bits that need to be skipped to avoid getting a negative number
        int skipBits = (PaillierEncryption.number_of_bits % maxBits);

        // Figure out where to start
        if(skipBits > 8)
        {
            // This may not work... idk.  But it won't get called for 1024 or 2048 bit key
            firstEmpty = true;
            bitPos = (16 - skipBits) - 1;
        }
        else if(skipBits == 8 || skipBits == 0)
        {
            // This is if the amount of ints that can be placed into a single BigInteger has no decimal place
            firstEmpty = true;
            bitPos = 7;
        }
        else
        {
            bitPos = (8 - skipBits) - 1;
        }

        // This byte is used to check if a specific bit is set or not
        byte andByte = 0x01;

        // This is the next byte to be added to byteArray
        byte nextByte = 0x00;

        // This is the current index of the byteArray
        int arrayIndex = 0;

        // This is the current index of the byteMatrix
        int matrixIndex = 0;

        // This is set when the matrix becomes properly filled
        boolean isDone = false;

        // Both the for loops just go through every bit
        // First loop is to make sure the alogorithm keeps filling the matrix even when there are no more numbers in inthist
        int i = 0;
        while(!isDone)
        {
            // Get the current value if there is a value to get
            int value = 0;
            if(i < intHist.length)
            {
                value = intHist[i];
            }

            // For every bit needed to represent value
            for(int k = maxBits - 1; k >= 0; k--)
            {
                // Check if we need to skip the first byte in byteArray
                // This should only happen in a few settings: 1024 bit key and a grid size of 1
                if(firstEmpty && arrayIndex == 0)
                {
                    byteArray[arrayIndex++] = nextByte;
                }
                // Check the specific bit of value
                if(((value >> k) & andByte) == 1)
                {
                    // if 1, set the proper bit in nextByte
                    nextByte |= (1 << bitPos);
                }
                // Always reduce the bit position by 1
                bitPos--;

                // If the bitPos is less than 0, then add nextByte to byteArray and reset values
                if(bitPos < 0)
                {
                    byteArray[arrayIndex++] = nextByte;
                    bitPos = 7;
                    nextByte = 0x00;
                }
                // If the array has been filled add it to the matrix and repeat
                if(arrayIndex == bytesNeeded)
                {
                    byteMatrix[matrixIndex++] = byteArray;
                    byteArray = new byte[bytesNeeded];
                    arrayIndex = 0;

                    if(matrixIndex == (int)Math.ceil(intHist.length / numInts))
                    {
                        isDone = true;
                    }

                    // Figure out where to start again
                    if(skipBits > 8)
                    {
                        // This may not work... idk.  But it won't get called for 1024 or 2048 bit key
                        bitPos = (16 - skipBits) - 1;
                    }
                    else if(skipBits == 8 || skipBits == 0)
                    {
                        // This is if the amount of ints that can be placed into a single BigInteger has no decimal place
                        bitPos = 7;
                    }
                    else
                    {
                        bitPos = (8 - skipBits) - 1;
                    }

                }

            }
            i++;
        }
//        BigInteger test = new BigInteger(Arrays.copyOfRange(byteHist, 0, 128));
    }


    /**
     * Returns the histogram
     *
     * @return int[][] histogram[grid_size][59]
     */
    public byte[][] getHistogram()
    {
        return byteMatrix;
    }

    public byte[][] getConcatHist()
    {
        return concatHist;
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
