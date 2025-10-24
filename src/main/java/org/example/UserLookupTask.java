package org.example;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserLookupTask implements Runnable {
    private final List<User> userSlice;
    private final ConcurrentMap<String, String> hashToPlain;
    private final AtomicInteger passwordsFound;

    public UserLookupTask(List<User> userSlice,
            ConcurrentMap<String, String> hashToPlain,
            AtomicInteger passwordsFound) {
        this.userSlice = userSlice;
        this.hashToPlain = hashToPlain;
        this.passwordsFound = passwordsFound;
    }

    @Override
    public void run() {
        for (User user : userSlice) {
            if (user == null || user.isFound)
                continue;

            // Look up user's hash in the dictionary
            String foundPassword = hashToPlain.get(user.hashedPassword);

            if (foundPassword != null) {
                synchronized (user) {
                    if (!user.isFound) { // Double-check
                        user.foundPassword = foundPassword;
                        user.isFound = true;
                        passwordsFound.incrementAndGet();
                    }
                }
            }
        }
    }

    // Inner class to hold user data
    static class User {
        String username;
        String hashedPassword;
        volatile boolean isFound = false;
        String foundPassword = null;

        public User(String username, String hashedPassword) {
            this.username = username;
            this.hashedPassword = hashedPassword;
        }
    }
}
