package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryAttack {

    static List<User> users = new ArrayList<>();
    static ConcurrentMap<String, String> hashToPlain = new ConcurrentHashMap<>();
    static AtomicInteger passwordsFound = new AtomicInteger(0);
    static AtomicInteger hashesComputed = new AtomicInteger(0);
    static AtomicInteger tasksCompleted = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java -jar ... <users> <dict> <output>");
            System.exit(1);
        }

        long time = runOnce(args[0], args[1], args[2]);
        System.out.println("");
        System.out.println("Total passwords found: " + passwordsFound.get());
        System.out.println("Total hashes computed: " + hashesComputed.get());
        System.out.println("Total time spent (milliseconds): " + time);
    }

    public static long runOnce(String usersPath, String dictPath, String outputPath)
            throws Exception {
        long start = System.currentTimeMillis();

        // Load dictionary and users
        List<String> allPasswords = loadDictionary(dictPath);
        loadUsers(usersPath);

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Build hash table and match users
        int estimatedSize = (int) (allPasswords.size() / 0.75f) + 1;
        hashToPlain = new ConcurrentHashMap<>(estimatedSize);
        buildHashLookupTable(executor, numThreads, allPasswords);

        ExecutorService progressExecutor = Executors.newSingleThreadExecutor();
        startProgressTracker(progressExecutor);
        matchUserPasswords(executor, numThreads);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        progressExecutor.shutdown();
        progressExecutor.awaitTermination(10, TimeUnit.MINUTES);

        long time = System.currentTimeMillis() - start;

        writeCrackedPasswordsToCSV(outputPath);

        return time;
    }

    /**
     * Builds a hash lookup table using multiple threads via ExecutorService
     */
    static void buildHashLookupTable(ExecutorService executor, int numChunks, List<String> allPasswords)
            throws InterruptedException {
        // Split dictionary into chunks for parallel processing
        int chunkSize = Math.max(1, allPasswords.size() / numChunks);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < allPasswords.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, allPasswords.size());
            List<String> slice = allPasswords.subList(i, end);

            Future<?> future = executor.submit(
                    new DictionaryHashTask(slice, hashToPlain, hashesComputed));
            futures.add(future);
        }

        // Wait for all hash computations to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                System.err.println("Error computing hashes: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Displays progress of dictionary attack for every 1000 tasks
     */
    static void startProgressTracker(ExecutorService executor) throws InterruptedException {
        executor.submit(new ProgressTrackerTask(users.size(), passwordsFound, tasksCompleted));
    }

    /**
     * Matches user password hashes against the pre-computed lookup table
     */
    static void matchUserPasswords(ExecutorService executor, int numChunks) throws InterruptedException {
        // System.out.println("Starting attack with " + users.size() + " total tasks...");

        // Split list of users into chunks for parallel processing
        int chunkSize = Math.max(1, users.size() / numChunks);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < users.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, users.size());
            List<User> slice = users.subList(i, end);

            Future<?> future = executor.submit(
                    new UserLookupTask(slice, hashToPlain, passwordsFound, tasksCompleted));
            futures.add(future);
        }

        // Wait for all user lookups to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                System.err.println("Error looking up user: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the successfully cracked user credentials to a CSV file.
     */
    static void writeCrackedPasswordsToCSV(String filePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8)) {
            writer.write("user_name,hashed_password,plain_password\n");
            StringBuilder sb = new StringBuilder(users.size() * 64);
            for (User user : users) {
                if (user.isFound) {
                    sb.append(user.username).append(',')
                            .append(user.hashedPassword).append(',')
                            .append(user.foundPassword).append('\n');
                }
            }
            writer.write(sb.toString());

            System.out.println("\nCracked password details have been written to " + filePath);
        } catch (IOException e) {
            System.err.println("Error: Could not write to CSV file: " + e.getMessage());
        }
    }

    // Load passwords & load users using BufferedReader
    static List<String> loadDictionary(String filePath) throws IOException {
        List<String> passwords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new FileReader(filePath, StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = reader.readLine()) != null) {
                passwords.add(line);
            }
        }
        return passwords;
    }

    static void loadUsers(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new FileReader(filename, StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2); // Limit split to 2 parts
                if (parts.length >= 2) {
                    users.add(new User(parts[0], parts[1]));
                }
            }
        }
    }

    static class User {
        String username;
        String hashedPassword;
        volatile boolean isFound = false;
        volatile String foundPassword = null;

        public User(String username, String hashedPassword) {
            this.username = username;
            this.hashedPassword = hashedPassword;
        }
    }
}