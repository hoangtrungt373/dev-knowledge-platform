package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Translates a standard 5-field UNIX/POSIX cron expression (minute, hour, day-of-month, month,
 * day-of-week) into a plain-English description, e.g. {@code "0 9 * * 1-5"} →
 * {@code "At 09:00, Monday through Friday"}. Declares {@link OperationGroup#INSPECTORS} — reads
 * meaning out of a value rather than transforming it, the same shape
 * {@code JwtDebuggerOperation}/{@code RegexTesterOperation}/{@code UrlParserOperation} already
 * establish for that group.
 *
 * <p><b>Scoped to standard POSIX/Vixie cron syntax only</b> — exactly 5 whitespace-separated
 * fields, each a comma-separated list of wildcards ({@code *}), single values, ranges
 * ({@code a-b}, including a wrap-around range like {@code FRI-MON}), and steps ({@code a-b/n},
 * {@code *&#47;n}, or the Vixie extension {@code a/n} meaning "{@code a} through the field's own
 * max, every {@code n}"). Month and day-of-week both additionally accept case-insensitive 3-letter
 * names ({@code JAN}–{@code DEC}, {@code SUN}–{@code SAT}); day-of-week accepts {@code 0}–{@code 7}
 * (both {@code 0} and {@code 7} mean Sunday, per POSIX). <b>Deliberately not supported</b>: the
 * 6/7-field variants some schedulers add (a leading seconds field, a trailing year field) or any
 * of Quartz's own extended syntax ({@code L}/{@code W}/{@code #}/{@code ?}) — this operation
 * requires exactly 5 fields and rejects anything else, the same "real syntax, not every variant
 * a similar-looking tool might accept" scoping {@code RegexTesterOperation}'s own JS-flags
 * translation already establishes.
 *
 * <p><b>Design: resolve each field to the concrete set of matching values first, then describe
 * the set</b> — not the field's own literal syntax. {@code "*&#47;5"} and the equivalent explicit list
 * {@code "0,5,10,...,55"} resolve to the exact same set and therefore produce the exact same
 * description; this sidesteps needing a special case for every syntactic way to write the same
 * schedule. Each resolved set is classified into exactly one of 5 shapes ({@link FieldValue}, a
 * sealed interface — Java 21's exhaustive {@code switch} over it below means adding a 6th shape
 * later is a compile error everywhere it isn't handled, not a silently-wrong runtime fallback):
 * {@link Every} (the set covers the field's entire valid range), {@link Single}, a
 * {@link ContiguousRange} (every value present, consecutively), a {@link SteppedRange} (a
 * constant-gap arithmetic progression that isn't contiguous), or an arbitrary {@link ListOf}
 * values that fits none of those. Minute and hour are then composed together into one time-of-day
 * clause ({@link #buildTimeClause}); day-of-month/month/day-of-week each get their own clause,
 * omitted entirely when {@link Every}. <b>Day-of-month and day-of-week, when *both* restricted
 * (neither is {@link Every}), are joined with "or"</b> — the real POSIX semantics (the schedule
 * fires when *either* field matches, not both at once) — rather than silently implying "and" the
 * way plain comma-joining every other clause already does.
 */
@Component
public class CronParserOperation implements DevUtilOperation {

    private sealed interface FieldValue permits Every, Single, ContiguousRange, SteppedRange, ListOf {
    }

    private record Every() implements FieldValue {
    }

    private record Single(int value) implements FieldValue {
    }

    private record ContiguousRange(int start, int end) implements FieldValue {
    }

    private record SteppedRange(int start, int end, int step) implements FieldValue {
    }

    private record ListOf(List<Integer> values) implements FieldValue {
    }

    private record FieldSpec(String name, int min, int max, Map<String, Integer> aliases) {
    }

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };
    private static final String[] DAY_NAMES = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    private static final FieldSpec MINUTE = new FieldSpec("minute", 0, 59, Map.of());
    private static final FieldSpec HOUR = new FieldSpec("hour", 0, 23, Map.of());
    private static final FieldSpec DAY_OF_MONTH = new FieldSpec("day-of-month", 1, 31, Map.of());
    private static final FieldSpec MONTH = new FieldSpec("month", 1, 12, aliasesFor(MONTH_NAMES, 1));
    // Parsed against 0-7 (7 tolerated as an alternate Sunday spelling per POSIX), then normalized
    // to canonical 0-6 immediately after parsing — see execute()'s own call to normalizeSunday.
    private static final FieldSpec DAY_OF_WEEK = new FieldSpec("day-of-week", 0, 7, aliasesFor(DAY_NAMES, 0));

    private static Map<String, Integer> aliasesFor(String[] names, int startValue) {
        Map<String, Integer> aliases = new java.util.HashMap<>();
        for (int i = 0; i < names.length; i++) {
            aliases.put(names[i].substring(0, 3).toUpperCase(Locale.ROOT), startValue + i);
        }
        return Map.copyOf(aliases);
    }

    @Override
    public OperationGroup group() {
        return OperationGroup.INSPECTORS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_CRON} when
     *                           {@code input} isn't exactly 5 whitespace-separated fields, or any
     *                           field's own syntax is malformed or out of range
     */
    public String execute(String input) {
        String[] fields = input.strip().split("\\s+");
        if (fields.length != 5) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                    "expected 5 fields (minute hour day-of-month month day-of-week), found " + fields.length));
        }

        SortedSet<Integer> minutes = parseField(fields[0], MINUTE);
        SortedSet<Integer> hours = parseField(fields[1], HOUR);
        SortedSet<Integer> daysOfMonth = parseField(fields[2], DAY_OF_MONTH);
        SortedSet<Integer> months = parseField(fields[3], MONTH);
        SortedSet<Integer> daysOfWeek = normalizeSunday(parseField(fields[4], DAY_OF_WEEK));

        FieldValue minute = classify(minutes, MINUTE.min(), MINUTE.max());
        FieldValue hour = classify(hours, HOUR.min(), HOUR.max());
        FieldValue dayOfMonth = classify(daysOfMonth, DAY_OF_MONTH.min(), DAY_OF_MONTH.max());
        FieldValue month = classify(months, MONTH.min(), MONTH.max());
        // Canonical 0-6 range for classification, after Sunday's 7→0 normalization above.
        FieldValue dayOfWeek = classify(daysOfWeek, 0, 6);

        return describe(minute, hour, dayOfMonth, month, dayOfWeek);
    }

    // Both 0 and 7 mean Sunday in POSIX cron — collapsing to 0 here (rather than during parsing)
    // keeps parseField itself generic across every field, with no per-field special case.
    private static SortedSet<Integer> normalizeSunday(SortedSet<Integer> daysOfWeek) {
        SortedSet<Integer> normalized = new TreeSet<>();
        for (int day : daysOfWeek) {
            normalized.add(day == 7 ? 0 : day);
        }
        return normalized;
    }

    /**
     * Parses one comma-separated cron field into the concrete set of values it matches within
     * {@code spec}'s own valid range — a wildcard becomes the field's entire range; a range whose
     * start is after its end wraps around (e.g. {@code FRI-MON} for day-of-week); a step with no
     * explicit range ({@code "5/10"}) runs from {@code 5} to the field's own max, per the Vixie
     * cron extension (not standard POSIX, but common enough to support).
     */
    private static SortedSet<Integer> parseField(String raw, FieldSpec spec) {
        SortedSet<Integer> result = new TreeSet<>();
        for (String term : raw.split(",")) {
            if (term.isEmpty()) {
                throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                        "empty term in " + spec.name() + " field"));
            }
            String base = term;
            int step = 1;
            int slash = term.indexOf('/');
            if (slash >= 0) {
                base = term.substring(0, slash);
                step = parseStep(term.substring(slash + 1), spec);
            }

            int start;
            int end;
            if (base.equals("*")) {
                start = spec.min();
                end = spec.max();
            } else if (base.contains("-")) {
                int dash = base.indexOf('-');
                start = resolveValue(base.substring(0, dash), spec);
                end = resolveValue(base.substring(dash + 1), spec);
            } else {
                start = resolveValue(base, spec);
                end = slash >= 0 ? spec.max() : start;
            }

            addRange(result, start, end, step, spec);
        }
        return result;
    }

    private static int parseStep(String token, FieldSpec spec) {
        int step;
        try {
            step = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                    "step is not a number: \"" + token + "\" in " + spec.name() + " field"));
        }
        if (step <= 0) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                    "step must be positive in " + spec.name() + " field, found " + step));
        }
        return step;
    }

    private static void addRange(SortedSet<Integer> result, int start, int end, int step, FieldSpec spec) {
        if (start <= end) {
            for (int v = start; v <= end; v += step) {
                result.add(v);
            }
        } else {
            // Wrap-around (e.g. FRI-MON): start..max, then continue the same step cadence from
            // whichever value in [min, start) the cadence would have landed on next.
            for (int v = start; v <= spec.max(); v += step) {
                result.add(v);
            }
            int remainder = (spec.max() - start + 1) % step;
            int wrapStart = remainder == 0 ? spec.min() : spec.min() + (step - remainder);
            for (int v = wrapStart; v <= end; v += step) {
                result.add(v);
            }
        }
        for (int v : result) {
            if (v < spec.min() || v > spec.max()) {
                throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                        "value " + v + " is out of range for " + spec.name()
                                + " (expected " + spec.min() + "-" + spec.max() + ")"));
            }
        }
    }

    private static int resolveValue(String token, FieldSpec spec) {
        Integer alias = spec.aliases().get(token.toUpperCase(Locale.ROOT));
        if (alias != null) {
            return alias;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_CRON, (Object) (
                    "not a number: \"" + token + "\" in " + spec.name() + " field"));
        }
    }

    private static FieldValue classify(SortedSet<Integer> values, int min, int max) {
        if (values.size() == max - min + 1) {
            return new Every();
        }
        if (values.size() == 1) {
            return new Single(values.first());
        }
        List<Integer> sorted = new ArrayList<>(values);
        boolean contiguous = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) != 1) {
                contiguous = false;
                break;
            }
        }
        if (contiguous) {
            return new ContiguousRange(sorted.get(0), sorted.get(sorted.size() - 1));
        }
        int step = sorted.get(1) - sorted.get(0);
        boolean arithmetic = true;
        for (int i = 2; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) != step) {
                arithmetic = false;
                break;
            }
        }
        if (arithmetic) {
            return new SteppedRange(sorted.get(0), sorted.get(sorted.size() - 1), step);
        }
        return new ListOf(sorted);
    }

    private static String describe(FieldValue minute, FieldValue hour, FieldValue dayOfMonth,
            FieldValue month, FieldValue dayOfWeek) {
        List<String> clauses = new ArrayList<>();
        clauses.add(buildTimeClause(minute, hour));

        String domClause = dayOfMonth instanceof Every ? null : describeDayOfMonth(dayOfMonth);
        String dowClause = dayOfWeek instanceof Every ? null : describeDayOfWeek(dayOfWeek);
        String monthClause = month instanceof Every ? null : describeMonth(month);

        // The real POSIX quirk: when day-of-month AND day-of-week are both restricted, the
        // schedule fires when EITHER matches — "or", not the implicit "and" every other clause
        // pairing in this sentence already carries via plain comma-joining.
        if (domClause != null && dowClause != null) {
            clauses.add(domClause + ", or " + dowClause);
        } else if (domClause != null) {
            clauses.add(domClause);
        }
        if (monthClause != null) {
            clauses.add(monthClause);
        }
        if (domClause == null && dowClause != null) {
            clauses.add(dowClause);
        }

        return String.join(", ", clauses);
    }

    /**
     * Composes minute+hour into one time-of-day clause — the two fields read far more naturally
     * together ("At 09:00") than as two independent clauses, so this is handled as a special pair
     * rather than through the same generic per-field describer month/day-of-week/day-of-month use.
     */
    private static String buildTimeClause(FieldValue minute, FieldValue hour) {
        if (minute instanceof Single m && hour instanceof Single h) {
            return "At " + pad2(h.value()) + ":" + pad2(m.value());
        }
        if (minute instanceof Every && hour instanceof Every) {
            return "Every minute";
        }
        if (hour instanceof Every && minute instanceof SteppedRange sr && sr.start() == MINUTE.min()) {
            return "Every " + sr.step() + " minutes";
        }
        if (minute instanceof Single m && m.value() == 0 && hour instanceof SteppedRange sr && sr.start() == HOUR.min()) {
            return "Every " + sr.step() + " hours";
        }
        if (hour instanceof Every && minute instanceof Single m) {
            return "At minute " + m.value() + " past every hour";
        }
        if (minute instanceof Every && hour instanceof Single h) {
            return "Every minute, between " + pad2(h.value()) + ":00 and " + pad2(h.value()) + ":59";
        }
        return "At minute(s) " + genericDescribe(minute, "minute") + ", hour(s) " + genericDescribe(hour, "hour");
    }

    private static String describeDayOfMonth(FieldValue value) {
        return switch (value) {
            case Every ignored -> null;
            case Single s -> "on day " + s.value() + " of the month";
            case ContiguousRange r -> "on days " + r.start() + " through " + r.end() + " of the month";
            case SteppedRange sr -> "every " + sr.step() + " days of the month, starting on day " + sr.start();
            case ListOf l -> "on days " + joinWithAnd(l.values().stream().map(String::valueOf).toList()) + " of the month";
        };
    }

    private static String describeMonth(FieldValue value) {
        return switch (value) {
            case Every ignored -> null;
            case Single s -> "only in " + MONTH_NAMES[s.value() - 1];
            case ContiguousRange r -> "from " + MONTH_NAMES[r.start() - 1] + " through " + MONTH_NAMES[r.end() - 1];
            case SteppedRange sr -> "every " + sr.step() + " months, starting in " + MONTH_NAMES[sr.start() - 1];
            case ListOf l -> "in " + joinWithAnd(l.values().stream().map(i -> MONTH_NAMES[i - 1]).toList());
        };
    }

    private static String describeDayOfWeek(FieldValue value) {
        return switch (value) {
            case Every ignored -> null;
            case Single s -> "only on " + DAY_NAMES[s.value()];
            case ContiguousRange r -> DAY_NAMES[r.start()] + " through " + DAY_NAMES[r.end()];
            case SteppedRange sr -> "every " + sr.step() + " days, starting " + DAY_NAMES[sr.start()];
            case ListOf l -> "on " + joinWithAnd(l.values().stream().map(i -> DAY_NAMES[i]).toList());
        };
    }

    /** Generic fallback for a minute/hour combination none of {@link #buildTimeClause}'s own
     * named cases fit — every/single/range/step/list, described plainly with no unit name baked
     * in (the caller supplies "minute"/"hour" itself). */
    private static String genericDescribe(FieldValue value, String unit) {
        return switch (value) {
            case Every ignored -> "every " + unit;
            case Single s -> String.valueOf(s.value());
            case ContiguousRange r -> r.start() + " through " + r.end();
            case SteppedRange sr -> "every " + sr.step() + " " + unit + "s from " + sr.start() + " through " + sr.end();
            case ListOf l -> joinWithAnd(l.values().stream().map(String::valueOf).toList());
        };
    }

    /** Oxford-comma join: {@code "a"} / {@code "a and b"} / {@code "a, b, and c"}. */
    private static String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < items.size() - 1; i++) {
            joined.append(items.get(i)).append(", ");
        }
        joined.append("and ").append(items.get(items.size() - 1));
        return joined.toString();
    }

    private static String pad2(int n) {
        return (n < 10 ? "0" : "") + n;
    }
}
