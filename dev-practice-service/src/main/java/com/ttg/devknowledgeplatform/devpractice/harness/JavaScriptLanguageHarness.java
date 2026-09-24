package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * JavaScript (Node.js), the second-simplest harness — {@code JSON.parse}/{@code JSON.stringify}
 * are native, so no embedded (de)serialization helper is needed (contrast
 * {@link JavaLanguageHarness}'s {@code JsonMini}). Follows real LeetCode's own JS convention: a
 * {@code var fn = function(...) {}} expression, not a class. Its starter template switches
 * Mustache delimiters to {@code <% %>}, since JSDoc's own {@code {type}} braces would otherwise
 * collide with Mustache's.
 */
@Component
public class JavaScriptLanguageHarness extends LanguageHarness {

    public JavaScriptLanguageHarness() {
        super(ProgrammingLanguage.JAVASCRIPT, new JavaScriptTypeRenderer(), Map.of());
    }
}
