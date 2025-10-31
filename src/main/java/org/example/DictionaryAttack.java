package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
        if (args.length >= 4 && args[0].equals("--bench")) {
            int iterations = Integer.parseInt(args[1]);
            String usersPath = args[2];
            String dictPath = args[3];
            String outPath = args.length > 4 ? args[4] : "out.txt";

            // Warm-up: run a few times to let JIT compile hot methods
            for (int i = 0; i < 5; i++) {
                runOnce(usersPath, dictPath, outPath, false);
            }

            long[] times = new long[iterations];
            for (int i = 0; i < iterations; i++) {
                times[i] = runOnce(usersPath, dictPath, outPath, i == iterations - 1); // write output only on last
                                                                                       // iteration
            }

            // Compute median
            Arrays.sort(times);
            long median = times[times.length / 2];
            System.out.println("");
            System.out.println("Total passwords found: " + passwordsFound.get());
            System.out.println("Total hashes computed: " + hashesComputed.get());
            System.out.println("Median time (ms): " + median);
            return;
        }

        // Existing normal main() logic
        if (args.length < 3) {
            System.out.println("Usage: java -jar ... <users> <dict> <output>");
            System.exit(1);
        } else {
            long time = runOnce(args[0], args[1], args[2], true);
            System.out.println("");
            System.out.println("Total passwords found: " + passwordsFound.get());
            System.out.println("Total hashes computed: " + hashesComputed.get());
            System.out.println("Total time spent (milliseconds): " + time);
        }
    }

    // inside DictionaryAttack.java
    public static long runOnce(String usersPath, String dictPath, String outputPath, boolean writeOutput)
            throws Exception {
        // Reset global state before each run
        users.clear();
        hashToPlain.clear();
        passwordsFound.set(0);
        hashesComputed.set(0);
        tasksCompleted.set(0);

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

        if (writeOutput) {
            writeCrackedPasswordsToCSV(outputPath);
        }

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
        File file = new File(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("user_name,hashed_password,plain_password\n");

            users.stream()
                    .filter(user -> user.isFound)
                    .map(user -> String.format("%s,%s,%s%n",
                            user.username,
                            user.hashedPassword,
                            user.foundPassword))
                    .forEach(line -> {
                        try {
                            writer.write(line);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

            System.out.println("\nCracked password details have been written to " + filePath);
        } catch (IOException e) {
            System.err.println("Error: Could not write to CSV file: " + e.getMessage());
        }
    }

    // Load passwords & load users using Streams
    // static List<String> loadDictionary(String filePath) throws IOException {
    // try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
    // return stream.parallel() // use parallel stream
    // .collect(Collectors.toList());
    // }
    // }

    // static void loadUsers(String filename) throws IOException {
    // try (Stream<String> stream = Files.lines(Paths.get(filename))) {
    // stream.parallel().forEach(line -> {
    // String[] parts = line.split(",");
    // if (parts.length >= 2) {
    // String username = parts[0];
    // String hashedPassword = parts[1];
    // users.put(username, new User(username, hashedPassword));
    // }
    // });
    // }
    // }

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