package com.ticketing.system.unit.infrastructure.persistence.UserPersistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

// Contract tests every IUserRepository implementation must satisfy. Future JPA-backed
// adapter will subclass this with its own newRepository() factory; tests are reused.
abstract class IUserRepositoryContractTest {

    protected abstract IUserRepository newRepository();

    private IUserRepository repo;

    @BeforeEach
    void setUp() {
        repo = newRepository();
    }

    @Test
    void save_thenFindByUsername_returnsTheSavedUser() {
        int id = repo.nextId();
        User user = new User(id, "alice", "alice@example.com", "HASH");
        repo.save(user);

        Optional<User> found = repo.findByUsername("alice");
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getUserId());
        assertEquals("alice@example.com", found.get().getEmail());
    }

    @Test
    void save_thenFindByEmail_returnsTheSavedUser() {
        int id = repo.nextId();
        User user = new User(id, "alice", "alice@example.com", "HASH");
        repo.save(user);

        Optional<User> found = repo.findByEmail("alice@example.com");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void findByUsername_returnsEmptyWhenMissing() {
        assertFalse(repo.findByUsername("ghost").isPresent());
    }

    @Test
    void findByEmail_returnsEmptyWhenMissing() {
        assertFalse(repo.findByEmail("ghost@example.com").isPresent());
    }

    @Test
    void existsByUsername_falseWhenMissingTrueWhenSaved() {
        assertFalse(repo.existsByUsername("alice"));
        repo.save(new User(repo.nextId(), "alice", "alice@example.com", "HASH"));
        assertTrue(repo.existsByUsername("alice"));
    }

    @Test
    void nextId_producesDistinctIncreasingValues() {
        int a = repo.nextId();
        int b = repo.nextId();
        int c = repo.nextId();
        assertNotEquals(a, b);
        assertNotEquals(b, c);
        assertTrue(b > a);
        assertTrue(c > b);
    }

    @Test
    void getUserById_returnsTheSavedUser() {
        int id = repo.nextId();
        User user = new User(id, "alice", "alice@example.com", "HASH");
        repo.save(user);

        User found = repo.getUserById(id);
        assertEquals("alice", found.getUsername());
    }

    @Test
    void getUserById_throwsWhenMissing() {
        assertThrows(UserNotFoundException.class, () -> repo.getUserById(9999));
    }

    @Test
    void delete_removesTheUser() {
        int id = repo.nextId();
        repo.save(new User(id, "alice", "alice@example.com", "HASH"));
        assertTrue(repo.existsByUsername("alice"));

        repo.delete(id);
        assertFalse(repo.existsByUsername("alice"));
    }
}
