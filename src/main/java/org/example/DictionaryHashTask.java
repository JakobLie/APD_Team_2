package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

// I worry if we have enough memory on jvm to do this
public class DictionaryHashTask implements Runnable {
    private final List<String> slice;
    private final ConcurrentMap<String, String> hashToPlain;
    private AtomicInteger hashesComputed;

    // ThreadLocal MessageDigest so each thread reuses a single SHA-256 instance
    private static final ThreadLocal<MessageDigest> THREAD_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    });

    public DictionaryHashTask(List<String> slice,
            ConcurrentMap<String, String> hashToPlain,
            AtomicInteger hashesComputed) {
        this.slice = slice;
        this.hashToPlain = hashToPlain;
        this.hashesComputed = hashesComputed;
    }

    @Override
    public void run() {
        // Get the per-thread MessageDigest
        final MessageDigest digest = THREAD_DIGEST.get();
        Map<String, String> localMap = new HashMap<>((int) (slice.size() / 0.75f) + 1);

        for (String plain : slice) {
            if (plain == null)
                continue;

            // reset before use to be safe
            digest.reset();
            byte[] hash = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            String hex = toHex(hash);
            localMap.putIfAbsent(hex, plain);
            hashesComputed.getAndIncrement();
        }
        hashToPlain.putAll(localMap);
    }

    // set as static attribute to avoid creating every call
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
