package com.ticketing.system.unit.infrastructure.persistence.EventPersistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;

// Contract tests every IEventRepository implementation must satisfy. Future JPA-backed
// adapter will subclass this with its own newRepository() factory; tests are reused.
public abstract class IEventRepositoryContractTest {
    
    protected abstract IEventRepository newRepository();

    private IEventRepository eventRepo;

    @BeforeEach
    void setUp() {
        eventRepo = newRepository();
    }

    // TODO: add tests for all repository methods, including search() with various filter combinations.    <---------------

    @Test
    void WhenSave_GivenValidEvent_returnsTheSavedEvent() {

    }


}
