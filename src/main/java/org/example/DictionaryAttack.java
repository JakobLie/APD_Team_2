package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        // Phase 1: Build hash lookup table using executor framework
        System.out.println("Phase 1: Building hash lookup table...");
        buildHashLookupTable(allPasswords);

        // Phase 2: Match user hashes against lookup table
        System.out.println("\nPhase 2: Matching user passwords...");
        matchUserPasswords();

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
    static void buildHashLookupTable(List<String> allPasswords) throws InterruptedException {
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Split dictionary into chunks for parallel processing
        int chunkSize = Math.max(1, allPasswords.size() / numThreads);
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

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("Hashes computed: " + hashesComputed.get());
    }

    /**
     * Matches user password hashes against the pre-computed lookup table
     */
    static void matchUserPasswords() {
        int totalUsers = users.size();
        int processedUsers = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println("Starting attack with " + totalUsers + " total tasks...");

        for (User user : users.values()) {
            String plainPassword = hashToPlain.get(user.hashedPassword);
            if (plainPassword != null) {
                user.isFound = true;
                user.foundPassword = plainPassword;
                passwordsFound.incrementAndGet();
            }

            processedUsers++;
            if (processedUsers % 1000 == 0 || processedUsers == totalUsers) {
                double progressPercent = (double) processedUsers / totalUsers * 100;
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                        timestamp, progressPercent, passwordsFound.get(), totalUsers - processedUsers);
            }
        }
        System.out.println(); // New line after progress complete
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

    static List<String> loadDictionary(String filePath) throws IOException {
        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            return stream.parallel() // use parallel stream
                    .collect(Collectors.toList());
        }
    }

    static void loadUsers(String filename) throws IOException {
        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
            stream.parallel().forEach(line -> {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String username = parts[0];
                    String hashedPassword = parts[1];
                    users.put(username, new User(username, hashedPassword));
                }
            });
        }
    }

    static class User {
        String username;
        String hashedPassword;
        boolean isFound = false;
        String foundPassword = null;

        public User(String username, String hashedPassword) {
            this.username = username;
            this.hashedPassword = hashedPassword;
        }
    }
}