package mst.nsh9b3.uface;

import android.util.Log;

import java.math.BigInteger;
import java.util.Random;

/**
 * Created by nick on 12/1/15.
 */
public class PaillierEncryption
{
    private static final String TAG = "uFace::Paillier";

    private BigInteger p, q, lambda;

    private BigInteger n;

    private BigInteger nsquare;

    private BigInteger g;

    private BigInteger u;

    private int bitLength;

    public PaillierEncryption() throws Exception
    {
        this(512, 64);
    }

    public PaillierEncryption(int bitLengthVal, int certainty) throws Exception
    {
        if (bitLengthVal < 128)
            throw new Exception("Paillier(int modLength): modLength must be >= 128");
        KeyGeneration(bitLengthVal, certainty);
    }

    public PaillierEncryption(String nString, String gString, String bitLengthString)
    {
        this.n = new BigInteger(nString);
        this.nsquare = n.multiply(n);
        this.g = new BigInteger(gString);
        this.bitLength = Integer.parseInt(bitLengthString);
    }

    public PaillierEncryption(String nString, String gString, String bitLengthString, String lamdaString, String uString)
    {
        this.n = new BigInteger(nString);
        this.nsquare = n.multiply(n);
        this.g = new BigInteger(gString);
        this.bitLength = Integer.parseInt(bitLengthString);
        this.lambda = new BigInteger(lamdaString);
        this.u = new BigInteger(uString);


    }

    private void KeyGeneration(int bitLengthVal, int certainty)
    {
        bitLength = bitLengthVal;

        // p = Random Prime Number
        p = new BigInteger(bitLength / 2, certainty, new Random());

        // q = DIFFERENT Random Prime Number
        do
        {
            q = new BigInteger(bitLength / 2, certainty, new Random());
        } while (q.compareTo(p) == 0);

        // n = p*q
        n = p.multiply(q);

        // nsquare = n*n
        nsquare = n.multiply(n);

        // lambda = lcm(p-1, q-1) = (p-1)*(q-1)/gcd(p-1, q-1)
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(
                p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));
        do
        {
            // g = random integer in Z*_{n*n}
            g = randomZStarNSquare();
        }
        while (g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).gcd(n).intValue() != 1);

        // u = (L(g^lambda mod n^2))^{-1} mod n, where L(u) = (u-1)/n
        u = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).modInverse(n);
    }

    private BigInteger Encryption(BigInteger m, BigInteger r)
    {
        // c = g^m * r^n mod n^2
        BigInteger encyptedValue = g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);
//        Log.d(TAG, "EncryptedValue: " + encyptedValue.toString());
        return encyptedValue;
    }

    public BigInteger Encryption(BigInteger m) throws Exception
    {
        // if m is not in Z_n
        if (m.compareTo(BigInteger.ZERO) < 0 || m.compareTo(n) >= 0)
        {
            throw new Exception("Paillier.encrypt(BigInteger m): plaintext m is not in Z_n");
        }
        // r = random integer in Z*_n
        BigInteger r = randomZStarN();
        return Encryption(m, r);
    }

    public BigInteger Decryption(BigInteger c) throws Exception
    {
        // if c is not in Z*_{n^2}
        if (c.compareTo(BigInteger.ZERO) < 0 || c.compareTo(nsquare) >= 0 || c.gcd(nsquare).intValue() != 1)
        {
            throw new Exception("Paillier.decrypt(BigInteger c): ciphertext c is not in Z*_{n^2}");
        }

        BigInteger decryptedValue = c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
//        Log.d(TAG, "DecryptedValue: " + decryptedValue.toString());
        return decryptedValue;
    }

    // return a random integer in Z_n
    public BigInteger randomZN()
    {
        BigInteger r;

        do
        {
            r = new BigInteger(bitLength, new Random());
        }
        while (r.compareTo(BigInteger.ZERO) <= 0 || r.compareTo(n) >= 0);

        return r;
    }

    // return a random integer in Z*_n
    private BigInteger randomZStarN()
    {
        BigInteger r;

        do
        {
            r = new BigInteger(bitLength, new Random());
        }
        while (r.compareTo(n) >= 0 || r.gcd(n).intValue() != 1);

        return r;
    }

    // return a random integer in Z*_{n^2}
    private BigInteger randomZStarNSquare()
    {
        BigInteger r;

        do
        {
            r = new BigInteger(bitLength * 2, new Random());
        }
        while (r.compareTo(nsquare) >= 0 || r.gcd(nsquare).intValue() != 1);

        return r;
    }

    public void publicKey()
    {
        Log.d(TAG, "n: " + n.toString());
        Log.d(TAG, "g: " + g.toString());
    }

    public void privateKey()
    {
        Log.d(TAG, "l: " + lambda.toString());
        Log.d(TAG, "u: " + u.toString());
    }

}
