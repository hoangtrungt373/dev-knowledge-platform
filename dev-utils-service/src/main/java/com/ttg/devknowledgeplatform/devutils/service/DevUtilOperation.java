package com.ttg.devknowledgeplatform.devutils.service;

/**
 * Interface for a dev-utils operation ({@code service.impl.JsonFormatOperation},
 * {@code YamlToJsonOperation}, {@code JsonToYamlOperation}, {@code HtmlBeautifyOperation}, and any
 * future operation) — its own concrete-type "Find Implementations" grouping already covers IDE
 * navigation, the same role {@code infra.event.ApplicationEventHandler}/
 * {@code infra.service.seed.Seeder} already play in this reactor; {@link #group()} (added
 * alongside {@link OperationGroup}, per direct request, once a second, genuinely different kind of
 * operation was planned) is the one piece of real, shared metadata every operation needs to
 * declare, surfaced to the GUI's own tool-sidebar section headlines.
 *
 * <p><b>Why no shared {@code execute(...)} signature, unlike a textbook GoF Strategy — this part is
 * unchanged by {@link #group()}'s addition.</b> An earlier revision of this interface forced every
 * implementation through {@code execute(String input, boolean minify): String} — reasonable while
 * every operation really was "text in, a minify flag, text out" (JSON format/validate, YAML↔JSON,
 * HTML beautify), but it broke down the moment a genuinely different-shaped operation was
 * discussed: a Unix Time Converter needs a timestamp + timezone + output format, a Number Base
 * Converter needs a value + two integer bases — neither fits "one string in, one bool flag, one
 * string out," and forcing either through that signature would mean packing multiple values into
 * one string and hand-parsing it back apart, worse in every way than a real typed parameter. A
 * shared method contract only pays for itself when something calls through it polymorphically (a
 * registry, a {@code List<DevUtilOperation>} loop) — nothing here does for execution itself:
 * {@code api.impl.DevUtilsController} injects and calls each operation by its own concrete type,
 * one fixed REST endpoint per operation, so there is no runtime dispatch-by-key an
 * {@code execute(...)} signature could ever have served. {@link #group()} is a different kind of
 * method, deliberately: it's not part of any operation's own input/output shape, just a fixed,
 * always-answerable piece of self-description ("which section do I belong in"), so requiring every
 * implementation to declare it doesn't reintroduce the "forced into one shape" problem this
 * interface was built to avoid — it's closer to {@code Object#toString()} than to a Strategy's own
 * {@code execute(...)}.
 *
 * <p>The REST-layer request/response DTOs follow the identical "share only where the shape
 * genuinely matches" rule — see {@code dto.MinifiableTextRequest}/{@code dto.TextRequest}/
 * {@code dto.DevUtilResponse}'s own Javadoc: operations that genuinely share a shape share a DTO,
 * one that doesn't gets its own, rather than every endpoint being forced through one shared
 * request/response pair.
 *
 * <p>Adding a future operation is still a new class implementing this interface (now including a
 * one-line {@link #group()} — a compile error if forgotten, deliberately: see that method's own
 * Javadoc for why an abstract method was chosen over a default one) plus one new controller
 * method — zero changes to any existing implementation (Open/Closed) for either concern — just
 * without a method signature dictating that operation's own parameter shape in advance.
 */
public interface DevUtilOperation {

    /**
     * Which {@link OperationGroup} this operation belongs to — every operation in this module
     * today returns {@link OperationGroup#FORMATTERS}. Deliberately an abstract method, not a
     * {@code default} one defaulting to {@code FORMATTERS}: a default would let a future,
     * genuinely-not-a-formatter operation (an encoder, an inspector, ...) silently inherit the
     * wrong group if its author forgot to override it — an abstract method turns that into a
     * compile error instead, forcing a conscious choice every time, the same "explicit over
     * silently-assumed" reasoning this interface's own Javadoc already applies to not sharing an
     * {@code execute(...)} signature.
     *
     * @return the group this operation's own sidebar entry renders under
     */
    OperationGroup group();
}
