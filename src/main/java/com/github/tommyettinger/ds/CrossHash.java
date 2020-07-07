package com.github.tommyettinger.ds;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static java.lang.Float.floatToIntBits;

/**
 * Code for hashing; this mostly consists of methods to hash arrays, and also has some utilities and nested classes.
 * Most of the methods here are like {@link #hash(char[])} or {@link #hash64(int[])}, hashing a whole array and giving a
 * 32-bit or 64-bit result, respectively. The algorithm used in the main part of the class is closely related to wyhash,
 * but doesn't produce the same results (it still passes stringent statistical tests in multiple variants of SMHasher).
 * <br>
 * Use {@link IHasher} implementations with {@link IndexedMap} and {@link IndexedSet} to permit normal Java arrays as
 * keys, which can be more efficient in some cases (arrays are still mutable, so they shouldn't be changed while used as
 * keys). You can get custom hash functors with {@link Curlup}, which has 2 to the 64 possible hash functions based on a
 * seed given at construction or in the call.
 * <br>
 * Utility functions here are {@link #nextPowerOfTwo(int)} (for int and long), and {@link #doubleToMixedIntBits(double)}
 * (which is mostly useful for converting all bits of a double into an int usable for hashing).
 * @author Tommy Ettinger
 */
public class CrossHash {

    /**
     * Return the least power of two greater than or equal to the specified value.
     * <br>
     * Note that this function will return 1 when the argument is 0.
     * <br>
     * This is a cleaned-up Java version of <a href="https://jameshfisher.com/2018/03/30/round-up-power-2/">this C code</a>.
     * @param x a non-negative int.
     * @return the least power of two greater than or equal to the specified value.
     */
    public static int nextPowerOfTwo(final int x) {
        return 1 << -Integer.numberOfLeadingZeros(x - 1);
    }

    /**
     * Return the least power of two greater than or equal to the specified value.
     * <br>
     * Note that this function will return 1 when the argument is 0.
     * <br>
     * This is a cleaned-up Java version of <a href="https://jameshfisher.com/2018/03/30/round-up-power-2/">this C code</a>.
     * @param x a non-negative long.
     * @return the least power of two greater than or equal to the specified value.
     */
    public static long nextPowerOfTwo(final long x) {
        return 1L << -Long.numberOfLeadingZeros(x - 1);
    }
    
    /**
     * Gets the 64-bit internal representation of a given {@code double}, XORs the upper and lower halves of that 64-bit
     * value, and returns the result as an int. Useful for mixing parts of a double's bits that are typically
     * not-very-well-distributed.
     * @param value any double (all NaN values will be treated the same)
     * @return an int that XORs the upper and lower halves of the bits that make up {@code value}
     */
    public static int doubleToMixedIntBits(final double value)
    {
        final long l = Double.doubleToLongBits(value);
        return (int)(l & 0xFFFFFFFFL) ^ (int)(l >>> 32);
    }
    /**
     * Big constant 0.
     */
    public static final long b0 = 0xA0761D6478BD642FL;
    /**
     * Big constant 1.
     */
    public static final long b1 = 0xE7037ED1A0B428DBL;
    /**
     * Big constant 2.
     */
    public static final long b2 = 0x8EBC6AF09C88C6E3L;
    /**
     * Big constant 3.
     */
    public static final long b3 = 0x589965CC75374CC3L;
    /**
     * Big constant 4.
     */
    public static final long b4 = 0x1D8E4E27C47D124FL;
    /**
     * Big constant 5.
     */
    public static final long b5 = 0xEB44ACCAB455D165L;

    /**
     * Takes two arguments that are technically longs, and should be very different, and uses them to get a result
     * that is technically a long and mixes the bits of the inputs. The arguments and result are only technically
     * longs because their lower 32 bits matter much more than their upper 32, and giving just any long won't work.
     * <br>
     * This is very similar to wyhash's mum function, but doesn't use 128-bit math because it expects that its
     * arguments are only relevant in their lower 32 bits (allowing their product to fit in 64 bits).
     * @param a a long that should probably only hold an int's worth of data
     * @param b a long that should probably only hold an int's worth of data
     * @return a sort-of randomized output dependent on both inputs
     */
    public static long mum(final long a, final long b) {
        final long n = a * b;
        return n - (n >>> 32);
    }

    /**
     * A slower but higher-quality variant on {@link #mum(long, long)} that can take two arbitrary longs (with any
     * of their 64 bits containing relevant data) instead of mum's 32-bit sections of its inputs, and outputs a
     * 64-bit result that can have any of its bits used.
     * <br>
     * This was changed so it distributes bits from both inputs a little better on July 6, 2019.
     * @param a any long
     * @param b any long
     * @return a sort-of randomized output dependent on both inputs
     */
    public static long wow(final long a, final long b) {
        final long n = (a ^ (b << 39 | b >>> 25)) * (b ^ (a << 39 | a >>> 25));
        return n ^ (n >>> 32);
    }

    public static long hash64(final boolean[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;//seed = b1 ^ b1 >>> 29 ^ b1 >>> 43 ^ b1 << 7 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum((data[i-3] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b1, (data[i-2] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b2) + seed,
                    mum((data[i-1] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b3, (data[i] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[len-1] ? 0x9E37L : 0x7F4AL), b3 ^ (data[len-1]  ? 0x79B9L : 0x7C15L)); break;
            case 2: seed = mum(seed ^ (data[len-2] ? 0x9E3779B9L : 0x7F4A7C15L), b0 ^ (data[len-1] ? 0x9E3779B9L : 0x7F4A7C15L)); break;
            case 3: seed = mum(seed ^ (data[len-3] ? 0x9E3779B9L : 0x7F4A7C15L), b2 ^ (data[len-2] ? 0x9E3779B9L : 0x7F4A7C15L)) ^ mum(seed ^ (data[len-1] ? 0x9E3779B9 : 0x7F4A7C15), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }
    public static long hash64(final byte[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b2, b1 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ b3, data[len-2] ^ data[len-1] << 8 ^ b4); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 8, b2 ^ data[len-1]); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final short[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final char[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final CharSequence data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length();
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data.charAt(i-3) ^ b1, data.charAt(i-2) ^ b2) + seed,
                    mum(data.charAt(i-1) ^ b3, data.charAt(i  ) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data.charAt(len-1)); break;
            case 2: seed = mum(seed ^ data.charAt(len-2), b3 ^ data.charAt(len-1)); break;
            case 3: seed = mum(seed ^ data.charAt(len-3) ^ data.charAt(len-2) << 16, b1 ^ data.charAt(len-1)); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final int[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[len-1] >>> 16), b3 ^ (data[len-1] & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ data[len-2], b0 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3], b2 ^ data[len-2]) ^ mum(seed ^ data[len-1], b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final int[] data, final int length) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        for (int i = 3; i < length; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (length & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[length-1] >>> 16), b3 ^ (data[length-1] & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ data[length-2], b0 ^ data[length-1]); break;
            case 3: seed = mum(seed ^ data[length-3], b2 ^ data[length-2]) ^ mum(seed ^ data[length-1], b4); break;
        }
        seed = (seed ^ seed << 16) * (length ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final long[] data) {
        if (data == null) return 0;
//          long seed = b0 ^ b0 >>> 23 ^ b0 >>> 48 ^ b0 << 7 ^ b0 << 53, 
//                    a = seed + b4, b = seed + b3,
//                    c = seed + b2, d = seed + b1;
        long seed = 0x1E98AE18CA351B28L,
                a = 0x3C26FC408EB22D77L, b = 0x773213E53F6C67EBL,
                c = 0xAD55190966BDE20BL, d = 0x59C2CEA6AE94403L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
            b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
            c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
            d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
            seed += a + b + c + d;
        }
        seed += b5;
        switch (len & 3) {
            case 1: seed = wow(seed, b1 ^ data[len-1]); break;
            case 2: seed = wow(seed + data[len-2], b2 + data[len-1]); break;
            case 3: seed = wow(seed + data[len-3], b2 + data[len-2]) ^ wow(seed + data[len-1], seed ^ b3); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0 ^ seed >>> 32);
        return seed - (seed >>> 31) + (seed << 33);
    }
    public static long hash64(final float[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(floatToIntBits(data[i-3]) ^ b1, floatToIntBits(data[i-2]) ^ b2) + seed,
                    mum(floatToIntBits(data[i-1]) ^ b3, floatToIntBits(data[i]) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (floatToIntBits(data[len-1]) >>> 16), b3 ^ (floatToIntBits(data[len-1]) & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ floatToIntBits(data[len-2]), b0 ^ floatToIntBits(data[len-1])); break;
            case 3: seed = mum(seed ^ floatToIntBits(data[len-3]), b2 ^ floatToIntBits(data[len-2])) ^ mum(seed ^ floatToIntBits(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }
    public static long hash64(final double[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(doubleToMixedIntBits(data[i-3]) ^ b1, doubleToMixedIntBits(data[i-2]) ^ b2) + seed,
                    mum(doubleToMixedIntBits(data[i-1]) ^ b3, doubleToMixedIntBits(data[i]) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (doubleToMixedIntBits(data[len-1]) >>> 16), b3 ^ (doubleToMixedIntBits(data[len-1]) & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ doubleToMixedIntBits(data[len-2]), b0 ^ doubleToMixedIntBits(data[len-1])); break;
            case 3: seed = mum(seed ^ doubleToMixedIntBits(data[len-3]), b2 ^ doubleToMixedIntBits(data[len-2])) ^ mum(seed ^ doubleToMixedIntBits(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    /**
     * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
     *
     * @param data  the char array to hash
     * @param start the start of the section to hash (inclusive)
     * @param end   the end of the section to hash (exclusive)
     * @return a 64-bit hash code for the requested section of data
     */
    public static long hash64(final char[] data, final int start, final int end) {
        if (data == null || start >= end)
            return 0;
        long seed = 9069147967908697017L;
        final int len = Math.min(end, data.length);
        for (int i = start + 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len - start & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        return mum(seed ^ seed << 16, len - start ^ b0);
    }

    /**
     * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
     *
     * @param data  the String or other CharSequence to hash
     * @param start the start of the section to hash (inclusive)
     * @param end   the end of the section to hash (exclusive)
     * @return a 64-bit hash code for the requested section of data
     */
    public static long hash64(final CharSequence data, final int start, final int end) {
        if (data == null || start >= end)
            return 0;
        long seed = 9069147967908697017L;
        final int len = Math.min(end, data.length());
        for (int i = start + 3; i < len; i+=4) {
            seed = mum(
                    mum(data.charAt(i-3) ^ b1, data.charAt(i-2) ^ b2) + seed,
                    mum(data.charAt(i-1) ^ b3, data.charAt(i) ^ b4));
        }
        switch (len - start & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data.charAt(len-1)); break;
            case 2: seed = mum(seed ^ data.charAt(len-2), b3 ^ data.charAt(len-1)); break;
            case 3: seed = mum(seed ^ data.charAt(len-3) ^ data.charAt(len-2) << 16, b1 ^ data.charAt(len-1)); break;
        }
        return mum(seed ^ seed << 16, len - start ^ b0);
    }


    public static long hash64(final char[][] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final int[][] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final long[][] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final CharSequence[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final CharSequence[]... data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final Iterable<? extends CharSequence> data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final Iterator<? extends CharSequence> it = data.iterator();
        int len = 0;
        while (it.hasNext())
        {
            ++len;
            seed = mum(
                    mum(hash(it.next()) ^ b1, (it.hasNext() ? hash(it.next()) ^ b2 ^ ++len : b2)) + seed,
                    mum((it.hasNext() ? hash(it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(it.next()) ^ b4 ^ ++len : b4)));
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final List<? extends CharSequence> data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.size();
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data.get(i-3)) ^ b1, hash(data.get(i-2)) ^ b2) + seed,
                    mum(hash(data.get(i-1)) ^ b3, hash(data.get(i  )) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data.get(len-1))) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data.get(len-2)), b0 ^ hash(data.get(len-1))); break;
            case 3: seed = mum(seed ^ hash(data.get(len-3)), b2 ^ hash(data.get(len-2))) ^ mum(seed ^ hash(data.get(len-1)), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);

    }

    public static long hash64(final Object[] data) {
        if (data == null) return 0;
        long seed = 9069147967908697017L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0);
        return seed - (seed >>> 31) + (seed << 33);
    }

    public static long hash64(final Object data) {
        if (data == null)
            return 0;
        final long h = data.hashCode() * 0x9E3779B97F4A7C15L;
        return h - (h >>> 31) + (h << 33);
    }


    public static int hash(final boolean[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum((data[i-3] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b1, (data[i-2] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b2) + seed,
                    mum((data[i-1] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b3, (data[i] ? 0x9E3779B9L : 0x7F4A7C15L) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[len-1] ? 0x9E37L : 0x7F4AL), b3 ^ (data[len-1]  ? 0x79B9L : 0x7C15L)); break;
            case 2: seed = mum(seed ^ (data[len-2] ? 0x9E3779B9L : 0x7F4A7C15L), b0 ^ (data[len-1] ? 0x9E3779B9L : 0x7F4A7C15L)); break;
            case 3: seed = mum(seed ^ (data[len-3] ? 0x9E3779B9L : 0x7F4A7C15L), b2 ^ (data[len-2] ? 0x9E3779B9L : 0x7F4A7C15L)) ^ mum(seed ^ (data[len-1] ? 0x9E3779B9 : 0x7F4A7C15), b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }
    public static int hash(final byte[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b2, b1 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ b3, data[len-2] ^ data[len-1] << 8 ^ b4); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 8, b2 ^ data[len-1]); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final short[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final char[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final CharSequence data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length();
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data.charAt(i-3) ^ b1, data.charAt(i-2) ^ b2) + seed,
                    mum(data.charAt(i-1) ^ b3, data.charAt(i  ) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data.charAt(len-1)); break;
            case 2: seed = mum(seed ^ data.charAt(len-2), b3 ^ data.charAt(len-1)); break;
            case 3: seed = mum(seed ^ data.charAt(len-3) ^ data.charAt(len-2) << 16, b1 ^ data.charAt(len-1)); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }
    public static int hash(final int[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[len-1] >>> 16), b3 ^ (data[len-1] & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ data[len-2], b0 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3], b2 ^ data[len-2]) ^ mum(seed ^ data[len-1], b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }
    public static int hash(final int[] data, final int length) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        for (int i = 3; i < length; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (length & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (data[length-1] >>> 16), b3 ^ (data[length-1] & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ data[length-2], b0 ^ data[length-1]); break;
            case 3: seed = mum(seed ^ data[length-3], b2 ^ data[length-2]) ^ mum(seed ^ data[length-1], b4); break;
        }
        return (int) mum(seed ^ seed << 16, length ^ b0);
    }

    public static int hash(final long[] data) {
        if (data == null) return 0;
        //long seed = 0x1E98AE18CA351B28L,// seed = b0 ^ b0 >>> 23 ^ b0 >>> 48 ^ b0 << 7 ^ b0 << 53, 
//                    a = seed ^ b4, b = (seed << 17 | seed >>> 47) ^ b3,
//                    c = (seed << 31 | seed >>> 33) ^ b2, d = (seed << 47 | seed >>> 17) ^ b1;
        //a = 0x316E03F0E480967L, b = 0x4A8F1A6436771F2L,
        //        c = 0xEBA6E76493C491EFL, d = 0x6A97719DF7B84DC1L;
        long seed = 0x1E98AE18CA351B28L, a = 0x3C26FC408EB22D77L, b = 0x773213E53F6C67EBL, c = 0xAD55190966BDE20BL, d = 0x59C2CEA6AE94403L;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
            b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
            c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
            d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
            seed += a + b + c + d;
        }
        seed += b5;
        switch (len & 3) {
            case 1: seed = wow(seed, b1 ^ data[len-1]); break;
            case 2: seed = wow(seed + data[len-2], b2 + data[len-1]); break;
            case 3: seed = wow(seed + data[len-3], b2 + data[len-2]) ^ wow(seed + data[len-1], seed ^ b3); break;
        }
        seed = (seed ^ seed << 16) * (len ^ b0 ^ seed >>> 32);
        return (int)(seed - (seed >>> 32));
    }

    public static int hash(final float[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(floatToIntBits(data[i-3]) ^ b1, floatToIntBits(data[i-2]) ^ b2) + seed,
                    mum(floatToIntBits(data[i-1]) ^ b3, floatToIntBits(data[i]) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (floatToIntBits(data[len-1]) >>> 16), b3 ^ (floatToIntBits(data[len-1]) & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ floatToIntBits(data[len-2]), b0 ^ floatToIntBits(data[len-1])); break;
            case 3: seed = mum(seed ^ floatToIntBits(data[len-3]), b2 ^ floatToIntBits(data[len-2])) ^ mum(seed ^ floatToIntBits(data[len-1]), b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }
    
    public static int hash(final double[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(doubleToMixedIntBits(data[i-3]) ^ b1, doubleToMixedIntBits(data[i-2]) ^ b2) + seed,
                    mum(doubleToMixedIntBits(data[i-1]) ^ b3, doubleToMixedIntBits(data[i]) ^ b4));
        }
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ (doubleToMixedIntBits(data[len-1]) >>> 16), b3 ^ (doubleToMixedIntBits(data[len-1]) & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ doubleToMixedIntBits(data[len-2]), b0 ^ doubleToMixedIntBits(data[len-1])); break;
            case 3: seed = mum(seed ^ doubleToMixedIntBits(data[len-3]), b2 ^ doubleToMixedIntBits(data[len-2])) ^ mum(seed ^ doubleToMixedIntBits(data[len-1]), b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    /**
     * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
     *
     * @param data  the char array to hash
     * @param start the start of the section to hash (inclusive)
     * @param end   the end of the section to hash (exclusive)
     * @return a 32-bit hash code for the requested section of data
     */
    public static int hash(final char[] data, final int start, final int end) {
        if (data == null || start >= end)
            return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = Math.min(end, data.length);
        for (int i = start + 3; i < len; i+=4) {
            seed = mum(
                    mum(data[i-3] ^ b1, data[i-2] ^ b2) + seed,
                    mum(data[i-1] ^ b3, data[i] ^ b4));
        }
        switch (len - start & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data[len-1]); break;
            case 2: seed = mum(seed ^ data[len-2], b3 ^ data[len-1]); break;
            case 3: seed = mum(seed ^ data[len-3] ^ data[len-2] << 16, b1 ^ data[len-1]); break;
        }
        return (int) mum(seed ^ seed << 16, len - start ^ b0);
    }

    /**
     * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
     *
     * @param data  the String or other CharSequence to hash
     * @param start the start of the section to hash (inclusive)
     * @param end   the end of the section to hash (exclusive)
     * @return a 32-bit hash code for the requested section of data
     */
    public static int hash(final CharSequence data, final int start, final int end) {
        if (data == null || start >= end)
            return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = Math.min(end, data.length());
        for (int i = start + 3; i < len; i+=4) {
            seed = mum(
                    mum(data.charAt(i-3) ^ b1, data.charAt(i-2) ^ b2) + seed,
                    mum(data.charAt(i-1) ^ b3, data.charAt(i) ^ b4));
        }
        switch (len - start & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^ b3, b4 ^ data.charAt(len-1)); break;
            case 2: seed = mum(seed ^ data.charAt(len-2), b3 ^ data.charAt(len-1)); break;
            case 3: seed = mum(seed ^ data.charAt(len-3) ^ data.charAt(len-2) << 16, b1 ^ data.charAt(len-1)); break;
        }
        return (int) mum(seed ^ seed << 16, len - start ^ b0);
    }
    
    public static int hash(final Iterable<? extends CharSequence> data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final Iterator<? extends CharSequence> it = data.iterator();
        int len = 0;
        while (it.hasNext())
        {
            ++len;
            seed = mum(
                    mum(hash(it.next()) ^ b1, (it.hasNext() ? hash(it.next()) ^ b2 ^ ++len : b2)) + seed,
                    mum((it.hasNext() ? hash(it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(it.next()) ^ b4 ^ ++len : b4)));
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final List<? extends CharSequence> data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.size();
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data.get(i-3)) ^ b1, hash(data.get(i-2)) ^ b2) + seed,
                    mum(hash(data.get(i-1)) ^ b3, hash(data.get(i  )) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data.get(len-1))) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data.get(len-2)), b0 ^ hash(data.get(len-1))); break;
            case 3: seed = mum(seed ^ hash(data.get(len-3)), b2 ^ hash(data.get(len-2))) ^ mum(seed ^ hash(data.get(len-1)), b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final Object[] data) {
        if (data == null) return 0;
        long seed = -260224914646652572L;//b1 ^ b1 >>> 41 ^ b1 << 53;
        final int len = data.length;
        for (int i = 3; i < len; i+=4) {
            seed = mum(
                    mum(hash(data[i-3]) ^ b1, hash(data[i-2]) ^ b2) + seed,
                    mum(hash(data[i-1]) ^ b3, hash(data[i  ]) ^ b4));
        }
        int t;
        switch (len & 3) {
            case 0: seed = mum(b1 ^ seed, b4 + seed); break;
            case 1: seed = mum(seed ^((t = hash(data[len-1])) >>> 16), b3 ^ (t & 0xFFFFL)); break;
            case 2: seed = mum(seed ^ hash(data[len-2]), b0 ^ hash(data[len-1])); break;
            case 3: seed = mum(seed ^ hash(data[len-3]), b2 ^ hash(data[len-2])) ^ mum(seed ^ hash(data[len-1]), b4); break;
        }
        return (int) mum(seed ^ seed << 16, len ^ b0);
    }

    public static int hash(final Object data) {
        if (data == null)
            return 0;
        final int h = data.hashCode() * 0x9E375;
        return h ^ (h >>> 16);
    }
    
    /**
     * An interface that can be used to move the logic for the hashCode() and equals() methods from a class' methods to
     * an implementation of IHasher that certain collections in SquidLib can use. Primarily useful when the key type is
     * an array, which normally doesn't work as expected in Java hash-based collections, but can if the right collection
     * and IHasher are used.
     */
    public interface IHasher extends Serializable {
        /**
         * If data is a type that this IHasher can specifically hash, this method should use that specific hash; in
         * other situations, it should simply delegate to calling {@link Object#hashCode()} on data. The body of an
         * implementation of this method can be very small; for an IHasher that is meant for byte arrays, the body could
         * be: {@code return (data instanceof byte[]) ? CrossHash.Lightning.hash((byte[]) data) : data.hashCode();}
         *
         * @param data the Object to hash; this method should take any type but often has special behavior for one type
         * @return a 32-bit int hash code of data
         */
        int hash(final Object data);

        /**
         * Not all types you might want to use an IHasher on meaningfully implement .equals(), such as array types; in
         * these situations the areEqual method helps quickly check for equality by potentially having special logic for
         * the type this is meant to check. The body of implementations for this method can be fairly small; for byte
         * arrays, it looks like: {@code return left == right
         * || ((left instanceof byte[] && right instanceof byte[])
         * ? Arrays.equals((byte[]) left, (byte[]) right)
         * : Objects.equals(left, right));} , but for multidimensional arrays you should use the
         * {@link #equalityHelper(Object[], Object[], IHasher)} method with an IHasher for the inner arrays that are 1D
         * or otherwise already-hash-able, as can be seen in the body of the implementation for 2D char arrays, where
         * charHasher is an existing IHasher that handles 1D arrays:
         * {@code return left == right
         * || ((left instanceof char[][] && right instanceof char[][])
         * ? equalityHelper((char[][]) left, (char[][]) right, charHasher)
         * : Objects.equals(left, right));}
         *
         * @param left  allowed to be null; most implementations will have special behavior for one type
         * @param right allowed to be null; most implementations will have special behavior for one type
         * @return true if left is equal to right (preferably by value, but reference equality may sometimes be needed)
         */
        boolean areEqual(final Object left, final Object right);
    }

    /**
     * Not a general-purpose method; meant to ease implementation of {@link IHasher#areEqual(Object, Object)}
     * methods when the type being compared is a multi-dimensional array (which normally requires the heavyweight method
     * {@link Arrays#deepEquals(Object[], Object[])} or doing more work yourself; this reduces the work needed to
     * implement fixed-depth equality). As mentioned in the docs for {@link IHasher#areEqual(Object, Object)}, example
     * code that hashes 2D char arrays can be done using an IHasher for 1D char arrays called charHasher:
     * {@code return left == right
     * || ((left instanceof char[][] && right instanceof char[][])
     * ? equalityHelper((char[][]) left, (char[][]) right, charHasher)
     * : Objects.equals(left, right));}
     *
     * @param left an array of some kind of Object, usually an array, that the given IHasher can compare
     * @param right an array of some kind of Object, usually an array, that the given IHasher can compare
     * @param inner an IHasher to compare items in left with items in right
     * @return true if the contents of left and right are equal by the given IHasher, otherwise false
     */
    public static boolean equalityHelper(Object[] left, Object[] right, IHasher inner) {
        if (left == right)
            return true;
        if (left == null || right == null || left.length != right.length)
            return false;
        for (int i = 0; i < left.length; i++) {
            if (!inner.areEqual(left[i], right[i]))
                return false;
        }
        return true;
    }

    private static class BooleanHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        BooleanHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof boolean[]) ? CrossHash.hash((boolean[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof boolean[] && right instanceof boolean[]) ? Arrays.equals((boolean[]) left, (boolean[]) right) : Objects.equals(left, right));
        }
    }

    public static final IHasher booleanHasher = new BooleanHasher();

    private static class ByteHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        ByteHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof byte[]) ? CrossHash.hash((byte[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right
                    || ((left instanceof byte[] && right instanceof byte[])
                    ? Arrays.equals((byte[]) left, (byte[]) right)
                    : Objects.equals(left, right));
        }
    }

    public static final IHasher byteHasher = new ByteHasher();

    private static class ShortHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        ShortHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof short[]) ? CrossHash.hash((short[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof short[] && right instanceof short[]) ? Arrays.equals((short[]) left, (short[]) right) : Objects.equals(left, right));
        }
    }

    public static final IHasher shortHasher = new ShortHasher();

    private static class CharHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        CharHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof char[]) ? CrossHash.hash((char[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof char[] && right instanceof char[]) ? Arrays.equals((char[]) left, (char[]) right) : Objects.equals(left, right));
        }
    }

    public static final IHasher charHasher = new CharHasher();

    private static class IntHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        IntHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof int[]) ? CrossHash.hash((int[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return (left instanceof int[] && right instanceof int[]) ? Arrays.equals((int[]) left, (int[]) right) : Objects.equals(left, right);
        }
    }

    public static final IHasher intHasher = new IntHasher();

    private static class LongHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        LongHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof long[]) ? CrossHash.hash((long[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return (left instanceof long[] && right instanceof long[]) ? Arrays.equals((long[]) left, (long[]) right) : Objects.equals(left, right);
        }
    }

    public static final IHasher longHasher = new LongHasher();

    private static class FloatHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        FloatHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof float[]) ? CrossHash.hash((float[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof float[] && right instanceof float[]) ? Arrays.equals((float[]) left, (float[]) right) : Objects.equals(left, right));
        }
    }

    public static final IHasher floatHasher = new FloatHasher();

    private static class DoubleHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        DoubleHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof double[]) ? CrossHash.hash((double[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof double[] && right instanceof double[]) ? Arrays.equals((double[]) left, (double[]) right) : Objects.equals(left, right));
        }
    }

    public static final IHasher doubleHasher = new DoubleHasher();

    private static class Char2DHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        Char2DHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof char[][]) ? CrossHash.hash((char[][]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right
                    || ((left instanceof char[][] && right instanceof char[][])
                    ? equalityHelper((char[][]) left, (char[][]) right, charHasher)
                    : Objects.equals(left, right));
        }
    }

    public static final IHasher char2DHasher = new Char2DHasher();

    private static class Int2DHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        Int2DHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof int[][]) ? CrossHash.hash((int[][]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right
                    || ((left instanceof int[][] && right instanceof int[][])
                    ? equalityHelper((int[][]) left, (int[][]) right, intHasher)
                    : Objects.equals(left, right));
        }
    }

    public static final IHasher int2DHasher = new Int2DHasher();

    private static class Long2DHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        Long2DHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof long[][]) ? CrossHash.hash((long[][]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right
                    || ((left instanceof long[][] && right instanceof long[][])
                    ? equalityHelper((long[][]) left, (long[][]) right, longHasher)
                    : Objects.equals(left, right));
        }
    }

    public static final IHasher long2DHasher = new Long2DHasher();

    private static class StringHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        StringHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof CharSequence) ? CrossHash.hash((CharSequence) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return Objects.equals(left, right);
        }
    }

    public static final IHasher stringHasher = new StringHasher();

    private static class StringArrayHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        StringArrayHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof CharSequence[]) ? CrossHash.hash((CharSequence[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof CharSequence[] && right instanceof CharSequence[]) ? equalityHelper((CharSequence[]) left, (CharSequence[]) right, stringHasher) : Objects.equals(left, right));
        }
    }

    /**
     * Though the name suggests this only hashes String arrays, it can actually hash any CharSequence array as well.
     */
    public static final IHasher stringArrayHasher = new StringArrayHasher();

    private static class ObjectArrayHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        ObjectArrayHasher() {
        }

        @Override
        public int hash(final Object data) {
            return (data instanceof Object[]) ? CrossHash.hash((Object[]) data) : data.hashCode();
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right || ((left instanceof Object[] && right instanceof Object[]) && Arrays.equals((Object[]) left, (Object[]) right) || Objects.equals(left, right));
        }
    }
    public static final IHasher objectArrayHasher = new ObjectArrayHasher();

    private static class DefaultHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 5L;

        DefaultHasher() {
        }

        @Override
        public int hash(final Object data) {
            if(data == null) return 0;
            final int x = data.hashCode() * 0x9E375;
            return x ^ x >>> 16;
        }

        @Override
        public boolean areEqual(final Object left, final Object right) {
            return (left == right) || (left != null && left.equals(right));
        }
    }

    public static final IHasher defaultHasher = new DefaultHasher();

    private static class MildHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 4L;

        MildHasher() {
        }

        @Override
        public int hash(final Object data) {
            return data != null ? data.hashCode() : 0;
        }

        @Override
        public boolean areEqual(final Object left, final Object right) {
            return (left == right) || (left != null && left.equals(right));
        }
    }

    /**
     * The most basic IHasher type; effectively delegates to {@link Objects#hashCode(Object)} and
     * {@link Objects#equals(Object, Object)}. This is the best IHasher to use when the Objects
     * being hashed have a strong hashCode() implementation already, since it avoids duplicating
     * the work that strong hashCode() already did.
     */
    public static final IHasher mildHasher = new MildHasher();

    private static class IdentityHasher implements IHasher, Serializable
    {
        private static final long serialVersionUID = 4L;
        IdentityHasher() { }

        @Override
        public int hash(Object data) {
            return System.identityHashCode(data);
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            return left == right;
        }
    }
    public static final IHasher identityHasher = new IdentityHasher();

    private static class GeneralHasher implements IHasher, Serializable {
        private static final long serialVersionUID = 3L;

        GeneralHasher() {
        }

        @Override
        public int hash(final Object data) {
            return CrossHash.hash(data);
        }

        @Override
        public boolean areEqual(Object left, Object right) {
            if(left == right) return true;
            Class<?> l = left.getClass(), r = right.getClass();
            if(l == r)
            {
                if(l.isArray())
                {
                    if(left instanceof int[]) return Arrays.equals((int[]) left, (int[]) right);
                    else if(left instanceof long[]) return Arrays.equals((long[]) left, (long[]) right);
                    else if(left instanceof char[]) return Arrays.equals((char[]) left, (char[]) right);
                    else if(left instanceof double[]) return Arrays.equals((double[]) left, (double[]) right);
                    else if(left instanceof boolean[]) return Arrays.equals((boolean[]) left, (boolean[]) right);
                    else if(left instanceof byte[]) return Arrays.equals((byte[]) left, (byte[]) right);
                    else if(left instanceof float[]) return Arrays.equals((float[]) left, (float[]) right);
                    else if(left instanceof short[]) return Arrays.equals((short[]) left, (short[]) right);
                    else if(left instanceof char[][]) return equalityHelper((char[][]) left, (char[][]) right, charHasher);
                    else if(left instanceof int[][]) return equalityHelper((int[][]) left, (int[][]) right, intHasher);
                    else if(left instanceof long[][]) return equalityHelper((long[][]) left, (long[][]) right, longHasher);
                    else if(left instanceof CharSequence[]) return equalityHelper((CharSequence[]) left, (CharSequence[]) right, stringHasher);
                    else if(left instanceof Object[]) return Arrays.equals((Object[]) left, (Object[]) right);
                }
                return Objects.equals(left, right);
            }
            return false;
        }
    }

    /**
     * This IHasher is the one you should use if you aren't totally certain what types will go in an IndexedMap's keys
     * or an IndexedSet's items, since it can handle mixes of elements.
     */
    public static final IHasher generalHasher = new GeneralHasher();

    /**
     * This is a class for hash functors, each an object with a 64-bit long seed. It uses an odd-but-fast
     * SIMD-friendly technique when hashing 32-bit items or smaller, and falls back to Yolk's algorithm when hashing
     * long values. If you are mainly hashing int arrays, short arrays, or byte arrays, this is probably the fastest
     * hash here unless the arrays are small (it outperforms all of the other hashes here on int arrays when those
     * arrays have length 50, and probably is faster than some sooner than that). Notably, on arrays 50 or longer this
     * runs in very close to half the time of {@link Arrays#hashCode(int[])}. This passes SMHasher for at least 64-bit
     * output. Has a lot of predefined functors (192, named after 24 Greek letters and 72 Goetic demons, see
     * <a href="https://en.wikipedia.org/wiki/Lesser_Key_of_Solomon#The_Seventy-Two_Demons">Wikipedia for the demons</a>,
     * in both lower case and lower case with a trailing underscore). You probably want to use {@link #predefined}
     * instead of wrangling demon names; you can always choose an element from predefined with a 7-bit number, and there
     * are 64 numbers outside that range so you can choose any of those when a functor must be different.
     * <br>
     * This hash is much more effective with large inputs because it takes advantage of HotSpot's optimizations for code
     * that looks like a dot product over part of an array. The general concept for this hash came from the "Unrolled"
     * hash in <a href="https://richardstartin.github.io/posts/collecting-rocks-and-benchmarks">one of Richard Startin's
     * blog posts</a>, which traces back to
     * <a href="http://mail.openjdk.java.net/pipermail/core-libs-dev/2014-September/028898.html">Peter Levart posting a
     * related improvement on String.hashCode()</a> in 2014. This isn't as fast as Startin's "Vectorized" hash, but this
     * works on variable array lengths and also passes SMHasher.
     * <br>
     * The name curlup comes from an M.C. Escher painting of a creature, whose name translates to curl-up, that could
     * walk on six legs to climb stairs, or roll at high speeds when the conditions were right.
     */
    public static final class Curlup {
        private final long seed;

        public Curlup(){
            this.seed = 0xC4CEB9FE1A85EC53L;
        }
        public Curlup(long seed)
        {
            this.seed = randomize(seed);
        }

        /**
         * Very similar to Pelican and related unary hashes; uses "xor rotate xor rotate" as an early step to mix any
         * clustered bits all around the result, then the rest is like MurmurHash3's mixer.
         * @param seed any long; there is no fix point at 0
         * @return any long
         */
        public static long randomize(long seed) {
            seed ^= (seed << 41 | seed >>> 23) ^ (seed << 17 | seed >>> 47) ^ 0xCB9C59B3F9F87D4DL;
            seed *= 0x369DEA0F31A53F85L;
            seed ^= seed >>> 31;
            seed *= 0xDB4F0B9175AE2165L;
            return seed ^ seed >>> 28;
        }

        public Curlup(final CharSequence seed)
        {
            this(CrossHash.hash64(seed));
        }

        public static final Curlup alpha = new Curlup("alpha"), beta = new Curlup("beta"), gamma = new Curlup("gamma"),
                delta = new Curlup("delta"), epsilon = new Curlup("epsilon"), zeta = new Curlup("zeta"),
                eta = new Curlup("eta"), theta = new Curlup("theta"), iota = new Curlup("iota"),
                kappa = new Curlup("kappa"), lambda = new Curlup("lambda"), mu = new Curlup("mu"),
                nu = new Curlup("nu"), xi = new Curlup("xi"), omicron = new Curlup("omicron"), pi = new Curlup("pi"),
                rho = new Curlup("rho"), sigma = new Curlup("sigma"), tau = new Curlup("tau"),
                upsilon = new Curlup("upsilon"), phi = new Curlup("phi"), chi = new Curlup("chi"), psi = new Curlup("psi"),
                omega = new Curlup("omega"),
                alpha_ = new Curlup("ALPHA"), beta_ = new Curlup("BETA"), gamma_ = new Curlup("GAMMA"),
                delta_ = new Curlup("DELTA"), epsilon_ = new Curlup("EPSILON"), zeta_ = new Curlup("ZETA"),
                eta_ = new Curlup("ETA"), theta_ = new Curlup("THETA"), iota_ = new Curlup("IOTA"),
                kappa_ = new Curlup("KAPPA"), lambda_ = new Curlup("LAMBDA"), mu_ = new Curlup("MU"),
                nu_ = new Curlup("NU"), xi_ = new Curlup("XI"), omicron_ = new Curlup("OMICRON"), pi_ = new Curlup("PI"),
                rho_ = new Curlup("RHO"), sigma_ = new Curlup("SIGMA"), tau_ = new Curlup("TAU"),
                upsilon_ = new Curlup("UPSILON"), phi_ = new Curlup("PHI"), chi_ = new Curlup("CHI"), psi_ = new Curlup("PSI"),
                omega_ = new Curlup("OMEGA"),
                baal = new Curlup("baal"), agares = new Curlup("agares"), vassago = new Curlup("vassago"), samigina = new Curlup("samigina"),
                marbas = new Curlup("marbas"), valefor = new Curlup("valefor"), amon = new Curlup("amon"), barbatos = new Curlup("barbatos"),
                paimon = new Curlup("paimon"), buer = new Curlup("buer"), gusion = new Curlup("gusion"), sitri = new Curlup("sitri"),
                beleth = new Curlup("beleth"), leraje = new Curlup("leraje"), eligos = new Curlup("eligos"), zepar = new Curlup("zepar"),
                botis = new Curlup("botis"), bathin = new Curlup("bathin"), sallos = new Curlup("sallos"), purson = new Curlup("purson"),
                marax = new Curlup("marax"), ipos = new Curlup("ipos"), aim = new Curlup("aim"), naberius = new Curlup("naberius"),
                glasya_labolas = new Curlup("glasya_labolas"), bune = new Curlup("bune"), ronove = new Curlup("ronove"), berith = new Curlup("berith"),
                astaroth = new Curlup("astaroth"), forneus = new Curlup("forneus"), foras = new Curlup("foras"), asmoday = new Curlup("asmoday"),
                gaap = new Curlup("gaap"), furfur = new Curlup("furfur"), marchosias = new Curlup("marchosias"), stolas = new Curlup("stolas"),
                phenex = new Curlup("phenex"), halphas = new Curlup("halphas"), malphas = new Curlup("malphas"), raum = new Curlup("raum"),
                focalor = new Curlup("focalor"), vepar = new Curlup("vepar"), sabnock = new Curlup("sabnock"), shax = new Curlup("shax"),
                vine = new Curlup("vine"), bifrons = new Curlup("bifrons"), vual = new Curlup("vual"), haagenti = new Curlup("haagenti"),
                crocell = new Curlup("crocell"), furcas = new Curlup("furcas"), balam = new Curlup("balam"), alloces = new Curlup("alloces"),
                caim = new Curlup("caim"), murmur = new Curlup("murmur"), orobas = new Curlup("orobas"), gremory = new Curlup("gremory"),
                ose = new Curlup("ose"), amy = new Curlup("amy"), orias = new Curlup("orias"), vapula = new Curlup("vapula"),
                zagan = new Curlup("zagan"), valac = new Curlup("valac"), andras = new Curlup("andras"), flauros = new Curlup("flauros"),
                andrealphus = new Curlup("andrealphus"), kimaris = new Curlup("kimaris"), amdusias = new Curlup("amdusias"), belial = new Curlup("belial"),
                decarabia = new Curlup("decarabia"), seere = new Curlup("seere"), dantalion = new Curlup("dantalion"), andromalius = new Curlup("andromalius"),
                baal_ = new Curlup("BAAL"), agares_ = new Curlup("AGARES"), vassago_ = new Curlup("VASSAGO"), samigina_ = new Curlup("SAMIGINA"),
                marbas_ = new Curlup("MARBAS"), valefor_ = new Curlup("VALEFOR"), amon_ = new Curlup("AMON"), barbatos_ = new Curlup("BARBATOS"),
                paimon_ = new Curlup("PAIMON"), buer_ = new Curlup("BUER"), gusion_ = new Curlup("GUSION"), sitri_ = new Curlup("SITRI"),
                beleth_ = new Curlup("BELETH"), leraje_ = new Curlup("LERAJE"), eligos_ = new Curlup("ELIGOS"), zepar_ = new Curlup("ZEPAR"),
                botis_ = new Curlup("BOTIS"), bathin_ = new Curlup("BATHIN"), sallos_ = new Curlup("SALLOS"), purson_ = new Curlup("PURSON"),
                marax_ = new Curlup("MARAX"), ipos_ = new Curlup("IPOS"), aim_ = new Curlup("AIM"), naberius_ = new Curlup("NABERIUS"),
                glasya_labolas_ = new Curlup("GLASYA_LABOLAS"), bune_ = new Curlup("BUNE"), ronove_ = new Curlup("RONOVE"), berith_ = new Curlup("BERITH"),
                astaroth_ = new Curlup("ASTAROTH"), forneus_ = new Curlup("FORNEUS"), foras_ = new Curlup("FORAS"), asmoday_ = new Curlup("ASMODAY"),
                gaap_ = new Curlup("GAAP"), furfur_ = new Curlup("FURFUR"), marchosias_ = new Curlup("MARCHOSIAS"), stolas_ = new Curlup("STOLAS"),
                phenex_ = new Curlup("PHENEX"), halphas_ = new Curlup("HALPHAS"), malphas_ = new Curlup("MALPHAS"), raum_ = new Curlup("RAUM"),
                focalor_ = new Curlup("FOCALOR"), vepar_ = new Curlup("VEPAR"), sabnock_ = new Curlup("SABNOCK"), shax_ = new Curlup("SHAX"),
                vine_ = new Curlup("VINE"), bifrons_ = new Curlup("BIFRONS"), vual_ = new Curlup("VUAL"), haagenti_ = new Curlup("HAAGENTI"),
                crocell_ = new Curlup("CROCELL"), furcas_ = new Curlup("FURCAS"), balam_ = new Curlup("BALAM"), alloces_ = new Curlup("ALLOCES"),
                caim_ = new Curlup("CAIM"), murmur_ = new Curlup("MURMUR"), orobas_ = new Curlup("OROBAS"), gremory_ = new Curlup("GREMORY"),
                ose_ = new Curlup("OSE"), amy_ = new Curlup("AMY"), orias_ = new Curlup("ORIAS"), vapula_ = new Curlup("VAPULA"),
                zagan_ = new Curlup("ZAGAN"), valac_ = new Curlup("VALAC"), andras_ = new Curlup("ANDRAS"), flauros_ = new Curlup("FLAUROS"),
                andrealphus_ = new Curlup("ANDREALPHUS"), kimaris_ = new Curlup("KIMARIS"), amdusias_ = new Curlup("AMDUSIAS"), belial_ = new Curlup("BELIAL"),
                decarabia_ = new Curlup("DECARABIA"), seere_ = new Curlup("SEERE"), dantalion_ = new Curlup("DANTALION"), andromalius_ = new Curlup("ANDROMALIUS")
                ;
        /**
         * Has a length of 192, which may be relevant if automatically choosing a predefined hash functor.
         */
        public static final Curlup[] predefined = new Curlup[]{alpha, beta, gamma, delta, epsilon, zeta, eta, theta, iota,
                kappa, lambda, mu, nu, xi, omicron, pi, rho, sigma, tau, upsilon, phi, chi, psi, omega,
                alpha_, beta_, gamma_, delta_, epsilon_, zeta_, eta_, theta_, iota_,
                kappa_, lambda_, mu_, nu_, xi_, omicron_, pi_, rho_, sigma_, tau_, upsilon_, phi_, chi_, psi_, omega_,
                baal, agares, vassago, samigina, marbas, valefor, amon, barbatos,
                paimon, buer, gusion, sitri, beleth, leraje, eligos, zepar,
                botis, bathin, sallos, purson, marax, ipos, aim, naberius,
                glasya_labolas, bune, ronove, berith, astaroth, forneus, foras, asmoday,
                gaap, furfur, marchosias, stolas, phenex, halphas, malphas, raum,
                focalor, vepar, sabnock, shax, vine, bifrons, vual, haagenti,
                crocell, furcas, balam, alloces, caim, murmur, orobas, gremory,
                ose, amy, orias, vapula, zagan, valac, andras, flauros,
                andrealphus, kimaris, amdusias, belial, decarabia, seere, dantalion, andromalius,
                baal_, agares_, vassago_, samigina_, marbas_, valefor_, amon_, barbatos_,
                paimon_, buer_, gusion_, sitri_, beleth_, leraje_, eligos_, zepar_,
                botis_, bathin_, sallos_, purson_, marax_, ipos_, aim_, naberius_,
                glasya_labolas_, bune_, ronove_, berith_, astaroth_, forneus_, foras_, asmoday_,
                gaap_, furfur_, marchosias_, stolas_, phenex_, halphas_, malphas_, raum_,
                focalor_, vepar_, sabnock_, shax_, vine_, bifrons_, vual_, haagenti_,
                crocell_, furcas_, balam_, alloces_, caim_, murmur_, orobas_, gremory_,
                ose_, amy_, orias_, vapula_, zagan_, valac_, andras_, flauros_,
                andrealphus_, kimaris_, amdusias_, belial_, decarabia_, seere_, dantalion_, andromalius_};


        public long hash64(final boolean[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  result      * 0xEBEDEED9D803C815L
                        + (data[i]     ? 0xD96EB1A810CAAF5FL : 0xCAAF5FD96EB1A810L)
                        + (data[i + 1] ? 0xC862B36DAF790DD5L : 0x790DD5C862B36DAFL)
                        + (data[i + 2] ? 0xB8ACD90C142FE10BL : 0x2FE10BB8ACD90C14L)
                        + (data[i + 3] ? 0xAA324F90DED86B69L : 0xD86B69AA324F90DEL)
                        + (data[i + 4] ? 0x9CDA5E693FEA10AFL : 0xEA10AF9CDA5E693FL)
                        + (data[i + 5] ? 0x908E3D2C82567A73L : 0x567A73908E3D2C82L)
                        + (data[i + 6] ? 0x8538ECB5BD456EA3L : 0x456EA38538ECB5BDL)
                        + (data[i + 7] ? 0xD1B54A32D192ED03L : 0x92ED03D1B54A32D1L)
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + (data[i] ? 0xEBEDEED9D803C815L : 0xD9D803C815EBEDEEL);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }
        public long hash64(final byte[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final short[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final char[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final CharSequence data) {
            if (data == null) return 0;
            final int length = data.length();
            long result = seed ^ length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final int[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final int[] data, final int length) {
            if (data == null) return 0;
            final int len = Math.min(length, data.length);
            long result = seed ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final long[] data) {
            if (data == null) return 0;
            long seed = this.seed, a = this.seed + b4, b = this.seed + b3, c = this.seed + b2, d = this.seed + b1;
            final int len = data.length;
            for (int i = 3; i < len; i+=4) {
                a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
                b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
                c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
                d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
                seed += a + b + c + d;
            }
            seed += b5;
            switch (len & 3) {
                case 1: seed = wow(seed, b1 ^ data[len-1]); break;
                case 2: seed = wow(seed + data[len-2], b2 + data[len-1]); break;
                case 3: seed = wow(seed + data[len-3], b2 + data[len-2]) ^ wow(seed + data[len-1], seed ^ b3); break;
            }
            seed = (seed ^ seed << 16) * (len ^ b0 ^ seed >>> 32);
            return seed - (seed >>> 31) + (seed << 33);
        }
        public long hash64(final float[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * floatToIntBits(data[i])
                        + 0xC862B36DAF790DD5L * floatToIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * floatToIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * floatToIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * floatToIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * floatToIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * floatToIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * floatToIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + floatToIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }
        public long hash64(final double[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * doubleToMixedIntBits(data[i])
                        + 0xC862B36DAF790DD5L * doubleToMixedIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * doubleToMixedIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * doubleToMixedIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * doubleToMixedIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * doubleToMixedIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * doubleToMixedIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * doubleToMixedIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + doubleToMixedIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the char array to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public long hash64(final char[] data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length);

            long result = seed ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the String or other CharSequence to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public long hash64(final CharSequence data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length());

            long result = seed ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }


        public long hash64(final char[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final int[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final long[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final CharSequence[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final CharSequence[]... data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final Iterable<? extends CharSequence> data) {
            if (data == null) return 0;
            long seed = this.seed;
            final Iterator<? extends CharSequence> it = data.iterator();
            int len = 0;
            while (it.hasNext())
            {
                ++len;
                seed = mum(
                        mum(hash(it.next()) ^ b1, (it.hasNext() ? hash(it.next()) ^ b2 ^ ++len : b2)) + seed,
                        mum((it.hasNext() ? hash(it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(it.next()) ^ b4 ^ ++len : b4)));
            }
            seed = (seed ^ seed << 16) * (len ^ b0);
            return seed - (seed >>> 31) + (seed << 33);
        }

        public long hash64(final List<? extends CharSequence> data) {
            if (data == null) return 0;
            final int len = data.size();
            long result = seed ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data.get(i))
                        + 0xC862B36DAF790DD5L * hash(data.get(i + 1))
                        + 0xB8ACD90C142FE10BL * hash(data.get(i + 2))
                        + 0xAA324F90DED86B69L * hash(data.get(i + 3))
                        + 0x9CDA5E693FEA10AFL * hash(data.get(i + 4))
                        + 0x908E3D2C82567A73L * hash(data.get(i + 5))
                        + 0x8538ECB5BD456EA3L * hash(data.get(i + 6))
                        + 0xD1B54A32D192ED03L * hash(data.get(i + 7))
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data.get(i));
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);

        }

        public long hash64(final Object[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public long hash64(final Object data) {
            if (data == null)
                return 0;
            final long h = (data.hashCode() + seed) * 0x9E3779B97F4A7C15L;
            return h - (h >>> 31) + (h << 33);
        }

        public int hash(final boolean[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  result      * 0xEBEDEED9D803C815L
                        + (data[i]     ? 0xD96EB1A810CAAF5FL : 0xCAAF5FD96EB1A810L)
                        + (data[i + 1] ? 0xC862B36DAF790DD5L : 0x790DD5C862B36DAFL)
                        + (data[i + 2] ? 0xB8ACD90C142FE10BL : 0x2FE10BB8ACD90C14L)
                        + (data[i + 3] ? 0xAA324F90DED86B69L : 0xD86B69AA324F90DEL)
                        + (data[i + 4] ? 0x9CDA5E693FEA10AFL : 0xEA10AF9CDA5E693FL)
                        + (data[i + 5] ? 0x908E3D2C82567A73L : 0x567A73908E3D2C82L)
                        + (data[i + 6] ? 0x8538ECB5BD456EA3L : 0x456EA38538ECB5BDL)
                        + (data[i + 7] ? 0xD1B54A32D192ED03L : 0x92ED03D1B54A32D1L)
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + (data[i] ? 0xEBEDEED9D803C815L : 0xD9D803C815EBEDEEL);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }
        public int hash(final byte[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final short[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final char[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final CharSequence data) {
            if (data == null) return 0;
            final int length = data.length();
            long result = seed ^ length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final int[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final int[] data, final int length) {
            if (data == null) return 0;
            final int len = Math.min(length, data.length);
            long result = seed ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final long[] data) {
            if (data == null) return 0;
            long seed = this.seed, a = this.seed + b4, b = this.seed + b3, c = this.seed + b2, d = this.seed + b1;
            final int len = data.length;
            for (int i = 3; i < len; i+=4) {
                a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
                b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
                c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
                d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
                seed += a + b + c + d;
            }
            seed += b5;
            switch (len & 3) {
                case 1: seed = wow(seed, b1 ^ data[len-1]); break;
                case 2: seed = wow(seed + data[len-2], b2 + data[len-1]); break;
                case 3: seed = wow(seed + data[len-3], b2 + data[len-2]) ^ wow(seed + data[len-1], seed ^ b3); break;
            }
            seed = (seed ^ seed << 16) * (len ^ b0 ^ seed >>> 32);
            return (int)(seed - (seed >>> 32));
        }

        public int hash(final float[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * floatToIntBits(data[i])
                        + 0xC862B36DAF790DD5L * floatToIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * floatToIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * floatToIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * floatToIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * floatToIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * floatToIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * floatToIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + floatToIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }
        public int hash(final double[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * doubleToMixedIntBits(data[i])
                        + 0xC862B36DAF790DD5L * doubleToMixedIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * doubleToMixedIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * doubleToMixedIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * doubleToMixedIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * doubleToMixedIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * doubleToMixedIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * doubleToMixedIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + doubleToMixedIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the char array to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public int hash(final char[] data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length);

            long result = seed ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the String or other CharSequence to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public int hash(final CharSequence data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length());

            long result = seed ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }


        public int hash(final char[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final int[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final long[][] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final CharSequence[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final CharSequence[]... data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final Iterable<? extends CharSequence> data) {
            if (data == null) return 0;
            long seed = this.seed;
            final Iterator<? extends CharSequence> it = data.iterator();
            int len = 0;
            while (it.hasNext())
            {
                ++len;
                seed = mum(
                        mum(hash(it.next()) ^ b1, (it.hasNext() ? hash(it.next()) ^ b2 ^ ++len : b2)) + seed,
                        mum((it.hasNext() ? hash(it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(it.next()) ^ b4 ^ ++len : b4)));
            }
            return (int) mum(seed ^ seed << 16, len ^ b0);
        }

        public int hash(final List<? extends CharSequence> data) {
            if (data == null) return 0;
            final int len = data.size();
            long result = seed ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data.get(i))
                        + 0xC862B36DAF790DD5L * hash(data.get(i + 1))
                        + 0xB8ACD90C142FE10BL * hash(data.get(i + 2))
                        + 0xAA324F90DED86B69L * hash(data.get(i + 3))
                        + 0x9CDA5E693FEA10AFL * hash(data.get(i + 4))
                        + 0x908E3D2C82567A73L * hash(data.get(i + 5))
                        + 0x8538ECB5BD456EA3L * hash(data.get(i + 6))
                        + 0xD1B54A32D192ED03L * hash(data.get(i + 7))
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data.get(i));
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);

        }

        public int hash(final Object[] data) {
            if (data == null) return 0;
            long result = seed ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(data[i])
                        + 0xC862B36DAF790DD5L * hash(data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(data[i + 2])
                        + 0xAA324F90DED86B69L * hash(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(data[i + 4])
                        + 0x908E3D2C82567A73L * hash(data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public int hash(final Object data) {
            if (data == null) return 0;
            return (int)((data.hashCode() + seed) * 0x9E3779B97F4A7C15L >>> 32);
        }

































        public static long hash64(final long seed, final boolean[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  result      * 0xEBEDEED9D803C815L
                        + (data[i]     ? 0xD96EB1A810CAAF5FL : 0xCAAF5FD96EB1A810L)
                        + (data[i + 1] ? 0xC862B36DAF790DD5L : 0x790DD5C862B36DAFL)
                        + (data[i + 2] ? 0xB8ACD90C142FE10BL : 0x2FE10BB8ACD90C14L)
                        + (data[i + 3] ? 0xAA324F90DED86B69L : 0xD86B69AA324F90DEL)
                        + (data[i + 4] ? 0x9CDA5E693FEA10AFL : 0xEA10AF9CDA5E693FL)
                        + (data[i + 5] ? 0x908E3D2C82567A73L : 0x567A73908E3D2C82L)
                        + (data[i + 6] ? 0x8538ECB5BD456EA3L : 0x456EA38538ECB5BDL)
                        + (data[i + 7] ? 0xD1B54A32D192ED03L : 0x92ED03D1B54A32D1L)
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + (data[i] ? 0xEBEDEED9D803C815L : 0xD9D803C815EBEDEEL);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }
        public static long hash64(final long seed, final byte[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final short[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final char[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final CharSequence data) {
            if (data == null) return 0;
            final int length = data.length();
            long result = randomize(seed) ^ length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final int[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final int[] data, final int length) {
            if (data == null) return 0;
            final int len = Math.min(length, data.length);
            long result = randomize(seed) ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final long[] data) {
            if (data == null) return 0;
            long s = randomize(seed), a = s + b4, b = s + b3, c = s + b2, d = s + b1;
            final int len = data.length;
            for (int i = 3; i < len; i+=4) {
                a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
                b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
                c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
                d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
                s += a + b + c + d;
            }
            s += b5;
            switch (len & 3) {
                case 1: s = wow(s, b1 ^ data[len-1]); break;
                case 2: s = wow(s + data[len-2], b2 + data[len-1]); break;
                case 3: s = wow(s + data[len-3], b2 + data[len-2]) ^ wow(s + data[len-1], s ^ b3); break;
            }
            s = (s ^ s << 16) * (len ^ b0 ^ s >>> 32);
            return s - (s >>> 31) + (s << 33);
        }
        public static long hash64(final long seed, final float[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * floatToIntBits(data[i])
                        + 0xC862B36DAF790DD5L * floatToIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * floatToIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * floatToIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * floatToIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * floatToIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * floatToIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * floatToIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + floatToIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }
        public static long hash64(final long seed, final double[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * doubleToMixedIntBits(data[i])
                        + 0xC862B36DAF790DD5L * doubleToMixedIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * doubleToMixedIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * doubleToMixedIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * doubleToMixedIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * doubleToMixedIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * doubleToMixedIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * doubleToMixedIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + doubleToMixedIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the char array to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public static long hash64(final long seed, final char[] data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length);

            long result = randomize(seed) ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the String or other CharSequence to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public static long hash64(final long seed, final CharSequence data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length());

            long result = randomize(seed) ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }


        public static long hash64(final long seed, final char[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final int[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final long[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final CharSequence[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final CharSequence[]... data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final Iterable<? extends CharSequence> data) {
            if (data == null) return 0;
            long s = randomize(seed);
            final Iterator<? extends CharSequence> it = data.iterator();
            int len = 0;
            while (it.hasNext())
            {
                ++len;
                s = mum(
                        mum(hash(seed, it.next()) ^ b1, (it.hasNext() ? hash(seed, it.next()) ^ b2 ^ ++len : b2)) + s,
                        mum((it.hasNext() ? hash(seed, it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(seed, it.next()) ^ b4 ^ ++len : b4)));
            }
            s = (s ^ s << 16) * (len ^ b0);
            return s - (s >>> 31) + (s << 33);
        }

        public static long hash64(final long seed, final List<? extends CharSequence> data) {
            if (data == null) return 0;
            final int len = data.size();
            long result = randomize(seed) ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data.get(i))
                        + 0xC862B36DAF790DD5L * hash(seed, data.get(i + 1))
                        + 0xB8ACD90C142FE10BL * hash(seed, data.get(i + 2))
                        + 0xAA324F90DED86B69L * hash(seed, data.get(i + 3))
                        + 0x9CDA5E693FEA10AFL * hash(seed, data.get(i + 4))
                        + 0x908E3D2C82567A73L * hash(seed, data.get(i + 5))
                        + 0x8538ECB5BD456EA3L * hash(seed, data.get(i + 6))
                        + 0xD1B54A32D192ED03L * hash(seed, data.get(i + 7))
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data.get(i));
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);

        }

        public static long hash64(final long seed, final Object[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (result ^ result >>> 28);
        }

        public static long hash64(final long seed, final Object data) {
            if (data == null)
                return 0;
            final long h = (data.hashCode() + randomize(seed)) * 0x9E3779B97F4A7C15L;
            return h - (h >>> 31) + (h << 33);
        }

        public static int hash(final long seed, final boolean[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  result      * 0xEBEDEED9D803C815L
                        + (data[i]     ? 0xD96EB1A810CAAF5FL : 0xCAAF5FD96EB1A810L)
                        + (data[i + 1] ? 0xC862B36DAF790DD5L : 0x790DD5C862B36DAFL)
                        + (data[i + 2] ? 0xB8ACD90C142FE10BL : 0x2FE10BB8ACD90C14L)
                        + (data[i + 3] ? 0xAA324F90DED86B69L : 0xD86B69AA324F90DEL)
                        + (data[i + 4] ? 0x9CDA5E693FEA10AFL : 0xEA10AF9CDA5E693FL)
                        + (data[i + 5] ? 0x908E3D2C82567A73L : 0x567A73908E3D2C82L)
                        + (data[i + 6] ? 0x8538ECB5BD456EA3L : 0x456EA38538ECB5BDL)
                        + (data[i + 7] ? 0xD1B54A32D192ED03L : 0x92ED03D1B54A32D1L)
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + (data[i] ? 0xEBEDEED9D803C815L : 0xD9D803C815EBEDEEL);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }
        public static int hash(final long seed, final byte[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final short[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final char[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final CharSequence data) {
            if (data == null) return 0;
            final int length = data.length();
            long result = randomize(seed) ^ length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final int[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final int[] data, final int length) {
            if (data == null) return 0;
            final int len = Math.min(length, data.length);
            long result = randomize(seed) ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final long[] data) {
            if (data == null) return 0;
            long s = randomize(seed), a = s + b4, b = s + b3, c = s + b2, d = s + b1;
            final int len = data.length;
            for (int i = 3; i < len; i+=4) {
                a ^= data[i-3] * b1; a = (a << 23 | a >>> 41) * b3;
                b ^= data[i-2] * b2; b = (b << 25 | b >>> 39) * b4;
                c ^= data[i-1] * b3; c = (c << 29 | c >>> 35) * b5;
                d ^= data[i  ] * b4; d = (d << 31 | d >>> 33) * b1;
                s += a + b + c + d;
            }
            s += b5;
            switch (len & 3) {
                case 1: s = wow(s, b1 ^ data[len-1]); break;
                case 2: s = wow(s + data[len-2], b2 + data[len-1]); break;
                case 3: s = wow(s + data[len-3], b2 + data[len-2]) ^ wow(s + data[len-1], s ^ b3); break;
            }
            s = (s ^ s << 16) * (len ^ b0 ^ s >>> 32);
            return (int)(s - (s >>> 32));
        }

        public static int hash(final long seed, final float[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * floatToIntBits(data[i])
                        + 0xC862B36DAF790DD5L * floatToIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * floatToIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * floatToIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * floatToIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * floatToIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * floatToIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * floatToIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + floatToIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }
        public static int hash(final long seed, final double[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * doubleToMixedIntBits(data[i])
                        + 0xC862B36DAF790DD5L * doubleToMixedIntBits(data[i + 1])
                        + 0xB8ACD90C142FE10BL * doubleToMixedIntBits(data[i + 2])
                        + 0xAA324F90DED86B69L * doubleToMixedIntBits(data[i + 3])
                        + 0x9CDA5E693FEA10AFL * doubleToMixedIntBits(data[i + 4])
                        + 0x908E3D2C82567A73L * doubleToMixedIntBits(data[i + 5])
                        + 0x8538ECB5BD456EA3L * doubleToMixedIntBits(data[i + 6])
                        + 0xD1B54A32D192ED03L * doubleToMixedIntBits(data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + doubleToMixedIntBits(data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the char array to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public static int hash(final long seed, final char[] data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length);

            long result = randomize(seed) ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data[i]
                        + 0xC862B36DAF790DD5L * data[i + 1]
                        + 0xB8ACD90C142FE10BL * data[i + 2]
                        + 0xAA324F90DED86B69L * data[i + 3]
                        + 0x9CDA5E693FEA10AFL * data[i + 4]
                        + 0x908E3D2C82567A73L * data[i + 5]
                        + 0x8538ECB5BD456EA3L * data[i + 6]
                        + 0xD1B54A32D192ED03L * data[i + 7]
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data[i];
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        /**
         * Hashes only a subsection of the given data, starting at start (inclusive) and ending before end (exclusive).
         *
         * @param data  the String or other CharSequence to hash
         * @param start the start of the section to hash (inclusive)
         * @param end   the end of the section to hash (exclusive)
         * @return a 64-bit hash code for the requested section of data
         */
        public static int hash(final long seed, final CharSequence data, final int start, final int end) {
            if (data == null || start >= end) return 0;
            final int len = Math.min(end, data.length());

            long result = randomize(seed) ^ (len - start) * 0x9E3779B97F4A7C15L;
            int i = start;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * data.charAt(i)
                        + 0xC862B36DAF790DD5L * data.charAt(i + 1)
                        + 0xB8ACD90C142FE10BL * data.charAt(i + 2)
                        + 0xAA324F90DED86B69L * data.charAt(i + 3)
                        + 0x9CDA5E693FEA10AFL * data.charAt(i + 4)
                        + 0x908E3D2C82567A73L * data.charAt(i + 5)
                        + 0x8538ECB5BD456EA3L * data.charAt(i + 6)
                        + 0xD1B54A32D192ED03L * data.charAt(i + 7)
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + data.charAt(i);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }


        public static int hash(final long seed, final char[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final int[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final long[][] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final CharSequence[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final CharSequence[]... data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final Iterable<? extends CharSequence> data) {
            if (data == null) return 0;
            long s = randomize(seed);
            final Iterator<? extends CharSequence> it = data.iterator();
            int len = 0;
            while (it.hasNext())
            {
                ++len;
                s = mum(
                        mum(hash(seed, it.next()) ^ b1, (it.hasNext() ? hash(seed, it.next()) ^ b2 ^ ++len : b2)) + s,
                        mum((it.hasNext() ? hash(seed, it.next()) ^ b3 ^ ++len : b3), (it.hasNext() ? hash(seed, it.next()) ^ b4 ^ ++len : b4)));
            }
            return (int) mum(s ^ s << 16, len ^ b0);
        }

        public static int hash(final long seed, final List<? extends CharSequence> data) {
            if (data == null) return 0;
            final int len = data.size();
            long result = randomize(seed) ^ len * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < len; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data.get(i))
                        + 0xC862B36DAF790DD5L * hash(seed, data.get(i + 1))
                        + 0xB8ACD90C142FE10BL * hash(seed, data.get(i + 2))
                        + 0xAA324F90DED86B69L * hash(seed, data.get(i + 3))
                        + 0x9CDA5E693FEA10AFL * hash(seed, data.get(i + 4))
                        + 0x908E3D2C82567A73L * hash(seed, data.get(i + 5))
                        + 0x8538ECB5BD456EA3L * hash(seed, data.get(i + 6))
                        + 0xD1B54A32D192ED03L * hash(seed, data.get(i + 7))
                ;
            }
            for (; i < len; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data.get(i));
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);

        }

        public static int hash(final long seed, final Object[] data) {
            if (data == null) return 0;
            long result = randomize(seed) ^ data.length * 0x9E3779B97F4A7C15L;
            int i = 0;
            for (; i + 7 < data.length; i += 8) {
                result =  0xEBEDEED9D803C815L * result
                        + 0xD96EB1A810CAAF5FL * hash(seed, data[i])
                        + 0xC862B36DAF790DD5L * hash(seed, data[i + 1])
                        + 0xB8ACD90C142FE10BL * hash(seed, data[i + 2])
                        + 0xAA324F90DED86B69L * hash(seed, data[i + 3])
                        + 0x9CDA5E693FEA10AFL * hash(seed, data[i + 4])
                        + 0x908E3D2C82567A73L * hash(seed, data[i + 5])
                        + 0x8538ECB5BD456EA3L * hash(seed, data[i + 6])
                        + 0xD1B54A32D192ED03L * hash(seed, data[i + 7])
                ;
            }
            for (; i < data.length; i++) {
                result = 0x9E3779B97F4A7C15L * result + hash(seed, data[i]);
            }
            result *= 0x94D049BB133111EBL;
            result ^= (result << 41 | result >>> 23) ^ (result << 17 | result >>> 47);
            result *= 0x369DEA0F31A53F85L;
            result ^= result >>> 31;
            result *= 0xDB4F0B9175AE2165L;
            return (int)(result ^ result >>> 28);
        }

        public static int hash(final long seed, final Object data) {
            if (data == null) return 0;
            return (int)((data.hashCode() + randomize(seed)) * 0x9E3779B97F4A7C15L >>> 32);
        }

    }
}
