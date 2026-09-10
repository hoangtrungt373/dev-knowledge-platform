package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

import jakarta.annotation.PreDestroy;

/**
 * Tests a regular expression against a block of text and returns every match found — the JS-style
 * "regex playground" experience (regex101/RegExr), not a real transform: {@code pattern} and
 * {@code testText} are read, never rewritten.
 *
 * <p>{@code flags} follows JS regex-literal convention, not Java's own {@code Pattern} flag
 * constants — translated by {@link #toJavaFlags}: {@code i} → {@link Pattern#CASE_INSENSITIVE} +
 * {@link Pattern#UNICODE_CASE}, {@code m} → {@link Pattern#MULTILINE}, {@code s} →
 * {@link Pattern#DOTALL}, {@code u} → {@link Pattern#UNICODE_CASE} again (JS's {@code u} enables
 * full-Unicode mode; Java has no single equivalent flag, so this is the closest one-flag
 * approximation). {@code g} (global) isn't a compile flag at all — it's a loop-vs-single-match
 * decision handled directly in {@link #findMatches}, matching how JS itself treats it (a
 * {@code String#match} call returns every match with {@code g}, only the first without it). Any
 * other character (JS's own {@code y}/sticky, {@code d}/indices, {@code v}/unicodeSets — none with
 * a Java equivalent at all) is silently ignored rather than rejected; this operation is meant to
 * feel like pasting a familiar {@code /pattern/flags} literal, not to validate that literal's own
 * flag syntax.
 *
 * <p><b>Every match is returned verbatim, blank-line separated</b> ({@code String#join("\n\n",
 * ...)}) — not one match per line with no separator, since a match can itself contain a newline
 * (a multiline {@code testText} matched by an {@code s}-flagged, {@code .}-heavy pattern), which
 * would otherwise be indistinguishable from a separate match. {@code "No matches found."} when
 * the pattern compiles but matches nothing — a normal, expected outcome while iterating on a
 * pattern, not a {@link BusinessException}.
 *
 * <p><b>Real failure path #1 — a pattern that fails to compile</b>: {@link DevUtilsErrorCode#INVALID_REGEX},
 * backed directly by {@link PatternSyntaxException#getMessage()} (already specific about the exact
 * character and position, e.g. {@code "Unclosed character class"}).
 *
 * <p><b>Real failure path #2 — catastrophic backtracking, a genuine risk on this fully public,
 * unauthenticated endpoint, not a theoretical one.</b> {@code java.util.regex} has no built-in
 * cancellation/timeout of its own once {@link Matcher#find()} starts, and — confirmed via a real
 * standalone Java harness before this was built, not assumed — a pattern like
 * {@code ^(.*)(.*)(.*)(.*)=x$} against a few hundred characters of non-matching input already
 * takes several seconds on this JDK and keeps growing from there (the textbook single-nested-
 * quantifier examples like {@code (a+)+$} turned out to no longer reproduce reliably on a modern
 * JDK — this module's own harness swept those first and found them resolving in milliseconds even
 * at 200+ characters, so a real exploit search had to look further than the classic tutorials
 * before landing on one that still works). A caller can submit an arbitrarily bad pattern against
 * up to {@link com.ttg.devknowledgeplatform.devutils.dto.DevUtilsLimits#MAX_INPUT_LENGTH}
 * characters of text with no authentication at all, so an unbounded match attempt would let one
 * request pin a CPU core indefinitely — a real, exploitable denial-of-service vector, not a
 * hypothetical one. Guarded by running the actual match loop ({@link #findMatches}) on a
 * dedicated virtual-thread executor with a {@link #MATCH_TIMEOUT} via {@link Future#get(long,
 * TimeUnit)}; a timeout throws {@link DevUtilsErrorCode#REGEX_TIMEOUT} instead of hanging the
 * calling request thread. <b>Known, accepted limitation</b>: {@code java.util.regex} offers no
 * cooperative cancellation, so the runaway matching thread itself isn't actually stopped by this
 * timeout — {@link Future#cancel(boolean)} only detaches the caller from it, it doesn't interrupt
 * a CPU-bound loop that never checks {@link Thread#isInterrupted()}. This bounds *response time*
 * per request (the actual goal — no request can hang the caller forever), not total CPU spent;
 * fully eliminating the latter would mean switching regex engines entirely (e.g. Google's RE2/RE2J,
 * a linear-time engine immune to catastrophic backtracking by construction) — a materially bigger
 * change than this operation's own scope, and not undertaken here.
 */
@Component
public class RegexTesterOperation implements DevUtilOperation {

    private static final int MATCH_TIMEOUT_SECONDS = 2;

    // Shared across every call to this singleton bean, not created per-request — a virtual-thread-
    // per-task executor is cheap enough to hand one task at a time regardless, and sharing one
    // instance avoids spinning up a fresh executor (and its own bookkeeping) on every submit.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public OperationGroup group() {
        return OperationGroup.INSPECTORS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_REGEX} when
     *                           {@code pattern} doesn't compile, or wrapping
     *                           {@link DevUtilsErrorCode#REGEX_TIMEOUT} when it compiles but takes
     *                           too long to evaluate against {@code testText} — see this class's
     *                           own Javadoc for both
     */
    public String execute(String pattern, String flags, String testText) {
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, toJavaFlags(flags));
        } catch (PatternSyntaxException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_REGEX, (Object) e.getMessage());
        }

        boolean global = flags != null && flags.indexOf('g') >= 0;
        Future<List<String>> future = executor.submit(() -> findMatches(compiled, testText, global));
        List<String> matches;
        try {
            matches = future.get(MATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BusinessException(DevUtilsErrorCode.REGEX_TIMEOUT, (Object) (
                    "pattern is too complex to evaluate against this text (possible catastrophic "
                            + "backtracking) — try a narrower pattern or shorter test text"));
        } catch (ExecutionException e) {
            // findMatches() has no failure path of its own once `compiled` is already a validated
            // Pattern — a defensive catch, not an expected path (the same "practically
            // unreachable, wrap and rethrow unchecked" treatment HashGeneratorOperation's own
            // NoSuchAlgorithmException catch already establishes).
            throw new AssertionError("Unexpected failure evaluating a pre-validated regex", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while evaluating a regex match", e);
        }

        return matches.isEmpty() ? "No matches found." : String.join("\n\n", matches);
    }

    private static List<String> findMatches(Pattern compiled, String testText, boolean global) {
        Matcher matcher = compiled.matcher(testText);
        List<String> matches = new ArrayList<>();
        if (global) {
            while (matcher.find()) {
                matches.add(matcher.group());
            }
        } else if (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    private static int toJavaFlags(String flags) {
        int javaFlags = 0;
        if (flags == null) {
            return javaFlags;
        }
        for (int i = 0; i < flags.length(); i++) {
            switch (flags.charAt(i)) {
                case 'i' -> javaFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                case 'm' -> javaFlags |= Pattern.MULTILINE;
                case 's' -> javaFlags |= Pattern.DOTALL;
                case 'u' -> javaFlags |= Pattern.UNICODE_CASE;
                default -> {
                    // 'g' is handled separately in execute() (a loop-vs-single-match decision, not
                    // a compile flag); every other character (JS-only y/d/v, or a typo) is ignored
                    // leniently rather than rejected — see this class's own Javadoc.
                }
            }
        }
        return javaFlags;
    }

    /** Releases the virtual-thread executor's own resources on application shutdown — cheap, but
     * correct hygiene for a long-lived thread pool owned by a singleton bean. */
    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
