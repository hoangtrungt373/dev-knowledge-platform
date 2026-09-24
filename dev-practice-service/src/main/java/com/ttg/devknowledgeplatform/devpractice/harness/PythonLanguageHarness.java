package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Python is the simplest of the three harnesses — {@code json}/{@code typing} are both stdlib, so
 * no embedded (de)serialization helper is needed (contrast {@link JavaLanguageHarness}'s
 * {@code JsonMini}); the generated entry point just {@code *args}-unpacks {@code json.loads(stdin)}
 * into the user's method.
 */
@Component
public class PythonLanguageHarness extends LanguageHarness {

    public PythonLanguageHarness() {
        super(ProgrammingLanguage.PYTHON, new PythonTypeRenderer(), Map.of());
    }
}
