package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Reads a Java template with JavaParser's real AST rather than a regex, so comments, annotations,
 * {@code final} parameters, generics and arbitrary line breaks are all handled the way javac would.
 * Accepts either a full class ({@code class Solution { ... }}) or just a bare method, which is
 * wrapped in a class before parsing.
 *
 * <p>Method selection: the template's one non-static method (a template declares the method users
 * implement, nothing else). Helper methods, or several candidates, are rejected with a clear
 * message rather than guessed between.
 */
@Component
public class JavaSignatureTemplateParser implements SignatureTemplateParser {

    private final TypeDeclarationIndex types =
            new TypeDeclarationIndex(new JavaTypeRenderer(), s -> s.replaceAll("\\s+", ""));

    // A fresh JavaParser per call: the class isn't thread-safe, and parsing a template is rare.
    private static final ParserConfiguration CONFIG =
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.JAVA;
    }

    @Override
    public ParsedSignature parse(String code) {
        CompilationUnit unit = parseUnit(code)
                .or(() -> parseUnit("class Solution {\n" + code + "\n}"))
                .orElseThrow(() -> TemplateParsing.invalid(
                        "not valid Java — expected e.g. `class Solution { public int evalRPN(String[] tokens) { } }`"));

        List<MethodDeclaration> methods = unit.findAll(MethodDeclaration.class).stream()
                .filter(m -> !m.isStatic())
                .toList();
        if (methods.isEmpty()) {
            throw TemplateParsing.invalid("no method found — declare the method users implement, "
                    + "e.g. `public int evalRPN(String[] tokens) { }`");
        }
        if (methods.size() > 1) {
            throw TemplateParsing.invalid("found " + methods.size() + " methods ("
                    + String.join(", ", methods.stream().map(MethodDeclaration::getNameAsString).toList())
                    + ") — a template declares only the one method users implement");
        }

        MethodDeclaration method = methods.get(0);
        requireSolutionClass(method);
        ParsedSignature.TypeChoice returnType = types.resolve(method.getType().asString(), "return type");
        List<ParsedSignature.ParsedParameter> parameters = method.getParameters().stream()
                .map(this::toParameter)
                .toList();
        return new ParsedSignature(method.getNameAsString(), returnType, parameters);
    }

    private ParsedSignature.ParsedParameter toParameter(Parameter parameter) {
        String name = parameter.getNameAsString();
        if (parameter.isVarArgs()) {
            throw TemplateParsing.invalid("parameter '" + name + "' is varargs — declare it as an array instead");
        }
        return new ParsedSignature.ParsedParameter(name,
                types.resolve(parameter.getType().asString(), "parameter '" + name + "'"));
    }

    /**
     * The harness always calls {@code new Solution()}, so a method written inside a class must be
     * in {@code Solution}. A bare method (no class at all) is fine — the starter code wraps it.
     * JavaParser 3.28 parses a bare method as a Java 21+ compact source file, inside a synthetic class
     * named {@code $COMPACT_CLASS} (regardless of the configured language level); a {@code $}-prefixed
     * name can never be user-written Java, so it's treated as "no class".
     */
    private static void requireSolutionClass(MethodDeclaration method) {
        method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .filter(name -> !name.equals("Solution") && !name.startsWith("$"))
                .ifPresent(name -> {
                    throw TemplateParsing.invalid("the method is in class '" + name
                            + "' — it must be in `class Solution` (that's the class submissions are run against)");
                });
    }

    private static Optional<CompilationUnit> parseUnit(String source) {
        ParseResult<CompilationUnit> result = new JavaParser(CONFIG).parse(source);
        return result.isSuccessful() ? result.getResult() : Optional.empty();
    }
}
