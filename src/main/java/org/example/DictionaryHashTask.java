package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

// I worry if we have enough memory on jvm to do this
public class DictionaryHashTask implements Runnable {
    private final List<String> slice;
    private final ConcurrentMap<String, String> hashToPlain;
    private AtomicInteger hashesComputed;

    public DictionaryHashTask(List<String> slice,
            ConcurrentMap<String, String> hashToPlain,
            AtomicInteger hashesComputed) {
        this.slice = slice;
        this.hashToPlain = hashToPlain;
        this.hashesComputed = hashesComputed;
    }

    @Override
    public void run() {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }

        for (String plain : slice) {
            if (plain == null)
                continue;
            byte[] hash = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            String hex = toHex(hash);
            hashToPlain.putIfAbsent(hex, plain);
            hashesComputed.getAndIncrement();
        }
    }

    // private static String toHex(byte[] bytes) {
    //     StringBuilder sb = new StringBuilder(bytes.length * 2);
    //     for (byte b : bytes)
    //         sb.append(String.format("%02x", b));
    //     return sb.toString();
    // }

    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        char[] hexArray = "0123456789abcdef".toCharArray();
        
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
