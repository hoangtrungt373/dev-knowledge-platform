package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.io.IOException;
import java.util.ArrayList;
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
 * <p><b>Every value comes back as a JSON string</b> — this operation never guesses at a cell's
 * "real" type (number, boolean). A CSV cell is text by definition; inferring a type is exactly the
 * kind of surprising, silently-lossy behavior a generic converter should avoid (a phone number or
 * ZIP code like {@code "007"} would lose its leading zero if reinterpreted as a number, and a
 * value like {@code "NA"}/{@code "1,000"} has no single unambiguous type to guess anyway). A
 * caller that needs typed values can convert specific fields itself once it has the JSON back.
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
        List<Map<String, String>> rows = parseCsv(input);
        return JsonNodeIo.write(objectMapper, rows, minify, DevUtilsErrorCode.INVALID_CSV);
    }

    private List<Map<String, String>> parseCsv(String input) {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        List<Map<String, String>> rows = new ArrayList<>();
        try (MappingIterator<Map<String, String>> it = csvMapper
                .readerFor(new TypeReference<Map<String, String>>() { })
                .with(schema)
                .readValues(input)) {
            while (it.hasNext()) {
                rows.add(it.next());
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
}
