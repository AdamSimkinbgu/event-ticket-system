Feature: Reserving a Ticket

  Scenario: Successfully reserving an available seat
    Given a logged-in Member "Alice"
    And an Event "Rock Concert" with available seats in "Zone A"
    When Alice adds a "Zone A" ticket to her cart
    Then the ticket should be locked for 10 minutes
