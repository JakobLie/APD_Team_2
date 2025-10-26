package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DictionaryAttack {

    static ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
    static ConcurrentMap<String, String> hashToPlain = new ConcurrentHashMap<>();
    static AtomicInteger passwordsFound = new AtomicInteger(0);
    static AtomicInteger hashesComputed = new AtomicInteger(0);
    static AtomicInteger tasksCompleted = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.out.println("Usage: java -jar <jar-file-name>.jar <input_file> <dictionary_file> <output_file>");
            System.exit(1);
        }

        String usersPath = args[0];
        String dictionaryPath = args[1];
        String passwordsPath = args[2];

        long start = System.currentTimeMillis();

        // Load dictionary and users
        List<String> allPasswords = loadDictionary(dictionaryPath);
        loadUsers(usersPath);

        // Phase 0: Init executor framework to be reused across phases
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Phase 1: Build hash lookup table using executor framework

        // Pre-allocate concurrent hash map capacity to prevent resizing
        int estimatedSize = (int) (allPasswords.size() / 0.75f) + 1;
        hashToPlain = new ConcurrentHashMap<>(estimatedSize);
        buildHashLookupTable(executor, numThreads, allPasswords);

        // Phase 2: Initiate progress tracker task using separate single thread executor framework
        ExecutorService progressExecutor = Executors.newSingleThreadExecutor();
        startProgressTracker(progressExecutor);

        // Phase 3: Match user hashes against lookup table using executor framework
        matchUserPasswords(executor, numThreads);

        // Clean up executor services
        progressExecutor.shutdown();
        progressExecutor.awaitTermination(10, TimeUnit.MINUTES);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("");
        System.out.println("Total passwords found: " + passwordsFound.get());
        System.out.println("Total hashes computed: " + hashesComputed.get());
        System.out.println("Total time spent (milliseconds): " + (System.currentTimeMillis() - start));

        if (passwordsFound.get() > 0) {
            writeCrackedPasswordsToCSV(passwordsPath);
        }
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
        System.out.println("Starting attack with " + users.size() + " total tasks...");

        // Convert HashMap to ArrayList to allow for splitting into chunks
        List<User> usersList = new ArrayList<>(users.values());

        // Split list of users into chunks for parallel processing
        int chunkSize = Math.max(1, usersList.size() / numChunks);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < usersList.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, usersList.size());
            List<User> slice = usersList.subList(i, end);

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

            users.values().stream()
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

    // Using Streams
    // static List<String> loadDictionary(String filePath) throws IOException {
    //     try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
    //         return stream.parallel() // use parallel stream
    //                 .collect(Collectors.toList());
    //     }
    // }

    // static void loadUsers(String filename) throws IOException {
    //     try (Stream<String> stream = Files.lines(Paths.get(filename))) {
    //         stream.parallel().forEach(line -> {
    //             String[] parts = line.split(",");
    //             if (parts.length >= 2) {
    //                 String username = parts[0];
    //                 String hashedPassword = parts[1];
    //                 users.put(username, new User(username, hashedPassword));
    //             }
    //         });
    //     }
    // }

    // using BufferedReader
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
                    users.put(parts[0], new User(parts[0], parts[1]));
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