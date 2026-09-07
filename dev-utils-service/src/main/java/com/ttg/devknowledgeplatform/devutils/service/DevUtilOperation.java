package com.ttg.devknowledgeplatform.devutils.service;

/**
 * Marker interface for a dev-utils operation ({@code service.impl.JsonFormatOperation},
 * {@code YamlToJsonOperation}, {@code JsonToYamlOperation}, {@code HtmlBeautifyOperation}, and any
 * future operation) — purely for IDE "Find Implementations" grouping and this Javadoc, the same
 * role {@code infra.event.ApplicationEventHandler}/{@code infra.service.seed.Seeder} already play
 * in this reactor. Deliberately declares **no method**.
 *
 * <p><b>Why no shared {@code execute(...)} signature, unlike a textbook GoF Strategy.</b> An
 * earlier revision of this interface forced every implementation through
 * {@code execute(String input, boolean minify): String} — reasonable while every operation really
 * was "text in, a minify flag, text out" (JSON format/validate, YAML↔JSON, HTML beautify), but it
 * broke down the moment a genuinely different-shaped operation was discussed: a Unix Time
 * Converter needs a timestamp + timezone + output format, a Number Base Converter needs a value +
 * two integer bases — neither fits "one string in, one bool flag, one string out," and forcing
 * either through that signature would mean packing multiple values into one string and hand-
 * parsing it back apart, worse in every way than a real typed parameter. A shared method contract
 * only pays for itself when something calls through it polymorphically (a registry, a
 * {@code List<DevUtilOperation>} loop) — nothing here does: {@code api.impl.DevUtilsController}
 * injects and calls each operation by its own concrete type, one fixed REST endpoint per
 * operation, so there is no runtime dispatch-by-key this interface's method signature could ever
 * have served. Each implementation is free to declare whatever parameter/return shape actually
 * fits its own operation.
 *
 * <p>The REST-layer request/response DTOs follow the identical rule — see
 * {@code dto.MinifiableTextRequest}/{@code dto.TextRequest}/{@code dto.DevUtilResponse}'s own
 * Javadoc: operations that genuinely share a shape share a DTO, one that doesn't gets its own,
 * rather than every endpoint being forced through one shared request/response pair.
 *
 * <p>Adding a future operation is still a new class implementing this interface plus one new
 * controller method — zero changes to any existing implementation (Open/Closed) — just without a
 * method signature dictating that operation's own parameter shape in advance.
 */
public interface DevUtilOperation {
}
