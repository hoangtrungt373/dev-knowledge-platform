package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.exception.ParsingExceptionMessages;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

import lombok.RequiredArgsConstructor;

/**
 * Converts raw CSV (first row treated as the header) into a JSON array of objects, pretty-printed
 * or (with {@code minify}) compact/single-line — the same pretty/minify choice
 * {@code JsonFormatOperation}/{@code YamlToJsonOperation} already apply to their own JSON output.
 *
 * <p><b>Every value comes back as a JSON string, except a cell that's exactly {@code true}/
 * {@code false} (case-insensitive) — those become real JSON booleans</b> — a deliberately narrow
 * exception, added after a direct request weighed against this class's own original "never guess
 * at a cell's type" reasoning. That reasoning still holds for every *other* type: inferring a
 * number is exactly the kind of surprising, silently-lossy behavior a generic converter should
 * avoid (a phone number or ZIP code like {@code "007"} would lose its leading zero if reinterpreted
 * as a number, and a value like {@code "NA"}/{@code "1,000"} has no single unambiguous type to
 * guess anyway) — numbers are deliberately still left as strings. Booleans are different: there's
 * no legitimate CSV convention where the literal text {@code true}/{@code false} needs to survive
 * as a *string* with some other meaning, so recognizing it loses nothing the way number-inference
 * would. Case-insensitive ({@code True}/{@code TRUE}/{@code false} all match) — CSV commonly comes
 * from tools (spreadsheet exports, other converters) that don't agree on a single casing, and
 * there's no ambiguity risk in matching more of them the same way there would be for numbers. A
 * caller that needs other fields typed can convert them itself once it has the JSON back.
 *
 * <p>Real failure path, backed by Jackson's own {@code CsvMapper} (a genuine CSV parser, not a
 * lenient textual reformatter) — {@code DevUtilsErrorCode.INVALID_CSV}, the same "real parse, real
 * invalid-input error" shape {@code INVALID_JSON}/{@code INVALID_YAML}/{@code INVALID_XML} already
 * establish. A row with a different column count than the header (a common, genuine CSV mistake —
 * a stray comma, a missing field) is exactly the kind of structural error this rejects.
 */
@Component
@RequiredArgsConstructor
public class CsvToJsonOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_CSV} when
     *                           {@code input} isn't structurally valid CSV against its own
     *                           header row
     */
    public String execute(String input, boolean minify) {
        List<Map<String, Object>> rows = parseCsv(input);
        return JsonNodeIo.write(objectMapper, rows, minify, DevUtilsErrorCode.INVALID_CSV);
    }

    private List<Map<String, Object>> parseCsv(String input) {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        // Read every cell as a plain String first — a CSV cell is text by definition, and Jackson's
        // own CSV reader has no notion of a typed column to read anything else into — then convert
        // each value via toCellValue() below, so the boolean-recognition rule lives in exactly one
        // place rather than being duplicated per row.
        List<Map<String, Object>> rows = new ArrayList<>();
        try (MappingIterator<Map<String, String>> it = csvMapper
                .readerFor(new TypeReference<Map<String, String>>() { })
                .with(schema)
                .readValues(input)) {
            while (it.hasNext()) {
                Map<String, String> rawRow = it.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<String, String> cell : rawRow.entrySet()) {
                    row.put(cell.getKey(), toCellValue(cell.getValue()));
                }
                rows.add(row);
            }
        } catch (RuntimeJsonMappingException e) {
            // MappingIterator#next() can't declare a checked exception (it implements
            // java.util.Iterator), so Jackson wraps a structural failure discovered mid-iteration
            // — e.g. a row with a different column count than the header — in this unchecked
            // wrapper instead of the IOException the try-with-resources close() below can still
            // throw. Caught by a real failing test, not anticipated up front: without this catch,
            // exactly the "different column count" case this class's own Javadoc calls out as the
            // reason INVALID_CSV exists went completely uncaught, surfacing as a raw 500 instead.
            Throwable cause = e.getCause();
            String message = cause instanceof JsonProcessingException jpe
                    ? ParsingExceptionMessages.friendlyMessage(jpe)
                    : e.getMessage();
            throw new BusinessException(DevUtilsErrorCode.INVALID_CSV, (Object) message);
        } catch (IOException e) {
            String message = e instanceof JsonProcessingException jpe
                    ? ParsingExceptionMessages.friendlyMessage(jpe)
                    : e.getMessage();
            throw new BusinessException(DevUtilsErrorCode.INVALID_CSV, (Object) message);
        }
        return rows;
    }

    /** A cell that's exactly {@code true}/{@code false} (case-insensitive) becomes a real
     * {@link Boolean} (so {@link JsonNodeIo#write} serializes it as a bare JSON {@code true}/
     * {@code false}, not a quoted string); every other cell stays the plain {@link String} Jackson's
     * CSV reader already produced. See this class's own Javadoc for why booleans get this treatment
     * and no other type does. */
    private static Object toCellValue(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        return raw;
    }
}
