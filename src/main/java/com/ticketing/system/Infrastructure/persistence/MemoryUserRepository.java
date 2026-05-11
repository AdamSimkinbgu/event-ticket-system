package com.ticketing.system.Infrastructure.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

@Repository
public class MemoryUserRepository implements IUserRepository {

    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);

    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }

    @Override
    public User getUserById(int targetId) {
        User user = usersById.get(targetId);
        if (user == null) {
            throw new UserNotFoundException(targetId);
        }
        return user;
    }

    @Override
    public boolean sendInvitation(int targetId, int companyId) {
        throw new UnsupportedOperationException("sendInvitation: not part of UC-11");
    }

    @Override
    public void updateUser(User targetUser) {
        usersById.put(targetUser.getUserId(), targetUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return usersById.values().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return usersById.values().stream()
                .filter(u -> email.equals(u.getEmail()))
                .findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public void save(User user) {
        usersById.put(user.getUserId(), user);
    }

    @Override
    public void delete(int userId) {
        usersById.remove(userId);
    }
}
