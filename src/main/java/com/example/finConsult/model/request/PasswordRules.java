package com.example.finConsult.model.request;

/**
 * Password complexity rule shared by every request that sets a password
 * (register, reset, change, create advisor). Used with {@code @Pattern}.
 */
public final class PasswordRules {

    // at least 8 characters, one lowercase, one uppercase, one digit and one special character (not a letter, digit or space)
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$";

    public static final String MESSAGE = "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a digit and a special character";

    private PasswordRules() {
    }
}
