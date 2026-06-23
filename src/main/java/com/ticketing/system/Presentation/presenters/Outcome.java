package com.ticketing.system.Presentation.presenters;

import com.ticketing.system.Presentation.components.ErrorPayload;

/**
 * Generic presenter outcome for simple read/query operations that don't
 * need per-exception failure variants. Presenters with rich failure
 * hierarchies (LoginPresenter, CheckoutPresenter) keep their own sealed
 * types — see docs/coding-standards.md for the full convention.
 */
public sealed interface Outcome<T> permits Outcome.Success, Outcome.Failure {
    record Success<T>(T value) implements Outcome<T> { }
    record Failure<T>(ErrorPayload error) implements Outcome<T> { }
}
