package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.User;
import com.bgu.se.ticketing.domain.repositories.IUserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure implementation of {@link IUserRepository} backed by Spring Data JPA.
 *
 * <p>This class is the Anti-Corruption Layer (ACL) between the domain and the JPA
 * persistence model. It translates between domain objects and JPA entities.
 */
@Component
public class UserRepositoryImpl implements IUserRepository {

    private final SpringUserRepository springRepo;

    public UserRepositoryImpl(SpringUserRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(String id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return springRepo.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springRepo.findByEmail(email).map(this::toDomain);
    }

    @Override
    public List<User> findAll() {
        return springRepo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        springRepo.deleteById(id);
    }

    @Override
    public boolean existsByUsername(String username) {
        return springRepo.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springRepo.existsByEmail(email);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.getId(), user.getUsername(),
                user.getEmail(), user.getPasswordHash(), user.getRole());
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getUsername(),
                entity.getEmail(), entity.getPasswordHash(), entity.getRole());
    }
}
