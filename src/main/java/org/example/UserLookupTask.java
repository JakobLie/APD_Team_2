package org.example;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserLookupTask implements Runnable {
    private final List<DictionaryAttack.User> userSlice;
    private final ConcurrentMap<String, String> hashToPlain;
    private AtomicInteger passwordsFound;
    private AtomicInteger tasksCompleted;

    public UserLookupTask(List<DictionaryAttack.User> userSlice,
            ConcurrentMap<String, String> hashToPlain,
            AtomicInteger passwordsFound,
            AtomicInteger tasksCompleted) {
        this.userSlice = userSlice;
        this.hashToPlain = hashToPlain;
        this.passwordsFound = passwordsFound;
        this.tasksCompleted = tasksCompleted;
    }

    @Override
    public void run() {
        for (DictionaryAttack.User user : userSlice) {
            String plainPassword = hashToPlain.get(user.hashedPassword);

            if (plainPassword != null) {
                user.isFound = true;
                user.foundPassword = plainPassword;
                passwordsFound.incrementAndGet();
            }

            tasksCompleted.incrementAndGet();
        }
    }
}
