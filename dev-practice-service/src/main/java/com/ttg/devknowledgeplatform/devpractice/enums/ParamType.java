package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * The closed vocabulary of value shapes a {@code Problem}'s method signature can use, for both its
 * parameters and its return type. Deliberately small and closed (not an open type system) — every
 * language harness has to know how to declare, parse from JSON, and serialize back to JSON each one
 * of these in its own language, so the vocabulary is exactly the set every harness actually
 * supports, not a superset. Adding a type here means: one new line in every
 * {@code harness.TypeRenderer} implementation (the compiler flags each one, via their exhaustive
 * switches), matching {@code toX}/{@code write} methods in {@code harness/java/JsonMini.java}, and
 * a new {@code sampleValue} in {@code LanguageHarnessExecutionIT} (also flagged by the compiler) to
 * prove the round-trip actually runs.
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
