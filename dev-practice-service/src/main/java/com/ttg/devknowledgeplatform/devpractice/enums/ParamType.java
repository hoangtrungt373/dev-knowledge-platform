package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * The closed vocabulary of value shapes a {@code Problem}'s method signature can use, for both its
 * parameters and its return type. Deliberately small and closed (not an open type system) — every
 * {@code harness.LanguageHarness} implementation has to know how to declare, parse from JSON, and
 * serialize back to JSON each one of these in its own language, so the vocabulary is exactly the
 * set every harness actually supports, not a superset. Adding a type here means adding matching
 * support in every {@code LanguageHarness} implementation.
 */
public enum ParamType {
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
    STRING,
    INT_ARRAY,
    DOUBLE_ARRAY,
    BOOLEAN_ARRAY,
    STRING_ARRAY,
    INT_MATRIX
}
