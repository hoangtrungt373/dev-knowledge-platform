package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw JSON array of flat objects (or a single JSON object, treated as one row) into
 * CSV. No {@code minify} concept — CSV is already the "compact" form, same reasoning
 * {@code JsonToYamlOperation} already documents for YAML's own lack of one (see
 * {@code dto.TextRequest}'s own Javadoc for why an operation with no minify concept gets that DTO
 * instead of {@code MinifiableTextRequest}).
 *
 * <p><b>Column set</b>: the union of every row's own field names, in first-seen order across all
 * rows — not just the first row's keys — so a heterogeneous array (rows that don't all share
 * exactly the same fields) still produces one consistent header covering every field seen
 * anywhere, with a blank cell for whichever rows don't have that field.
 *
 * <p><b>Cell values</b>: a scalar (string/number/boolean/null) is written via
 * {@link JsonNode#asText()}; a nested object or array value is written as its own compact JSON
 * string (via {@link JsonNode#toString()}) rather than flattened into further columns — CSV is
 * inherently a flat, two-dimensional format, so a genuinely nested value has no lossless flat
 * representation; re-embedding it as JSON text is the same trade-off most JSON-to-CSV tools make,
 * and keeps the conversion total (never silently drops data) rather than partial.
 *
 * <p>Real failure path, same "real parse, real invalid-input error" shape
 * {@code JsonFormatOperation} already establishes: a genuine JSON syntax error reuses
 * {@code DevUtilsErrorCode.INVALID_JSON} with {@code ParsingExceptionMessages}'s own clean
 * message; valid JSON that isn't shaped as an array of objects (or a lone object) — e.g. a bare
 * array of numbers, or a scalar at the root — reuses the same error code with a plain, specific
 * message, since it's still fundamentally "this JSON isn't valid input for this operation."
 *
 * <p><b>{@code CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING} is not optional — a real bug,
 * reported directly against a real payload.</b> {@link CsvMapper}'s own default ("loose") quoting
 * check quotes a cell value for containing *any* character below ASCII 45 (comma, the default
 * separator, plus one) — not just what RFC 4180 actually requires (the separator, the quote
 * character, or a line break) — so a value with nothing more than a plain space (e.g.
 * {@code "JSON Formatter"}) was silently rendered {@code "\"JSON Formatter\""}. Enabling this
 * feature switches to Jackson's own "strict" check, which quotes only when RFC 4180 requires it —
 * verified via a standalone harness against the actual resolved {@code jackson-dataformat-csv}
 * jar, including confirming a value that genuinely contains a quote/comma/newline is still
 * correctly quoted (with the internal quote doubled, per RFC 4180) with this flag set.
 */
@Component
@RequiredArgsConstructor
public class JsonToCsvOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    @Override
    public OperationGroup group() {
        return OperationGroup.FORMATTERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_JSON} when
     *                           {@code input} isn't valid JSON, or is valid JSON that isn't
     *                           shaped as an array of objects (or a lone object)
     */
    public String execute(String input) {
        JsonNode root = JsonNodeIo.readTree(objectMapper, input, DevUtilsErrorCode.INVALID_JSON);
        List<JsonNode> rows = extractRows(root);
        if (rows.isEmpty()) {
            return "";
        }

        List<String> columns = collectColumns(rows);
        List<Map<String, String>> rowMaps = buildRowMaps(rows, columns);

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        columns.forEach(schemaBuilder::addColumn);
        CsvSchema schema = schemaBuilder.build().withHeader();

        try {
            // STRICT_CHECK_FOR_QUOTING is not optional here — a real bug, reported directly
            // against a real payload, found by decompiling Jackson's own CsvEncoder rather than
            // guessing: CsvGenerator's default ("loose") quoting check quotes any cell value
            // containing *any* character below ASCII 45 (comma, the default separator, plus one) —
            // not just the RFC 4180-required comma/quote/newline — which silently quotes a value
            // for containing nothing more than a plain space (e.g. "JSON Formatter" -> "\"JSON
            // Formatter\""), a leading punctuation character, etc. Enabling this feature switches
            // to Jackson's own "strict" quoting check, which quotes only when RFC 4180 actually
            // requires it (the value itself contains the separator, the quote character, or a line
            // break) — verified via a standalone harness against the actual resolved
            // jackson-dataformat-csv:2.19.2 jar, including a value with a literal quote/comma/
            // newline still correctly quoted (with the internal quote doubled, per RFC 4180) once
            // this flag is set.
            CsvMapper csvMapper = CsvMapper.builder().enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING).build();
            return csvMapper.writer(schema).writeValueAsString(rowMaps);
        } catch (JsonProcessingException e) {
            // Only reachable if Jackson's own CSV writer rejects the schema/data shape we just
            // built ourselves — every cell value is already a plain String by this point, so this
            // is a defensive catch, not an expected path.
            throw new BusinessException(DevUtilsErrorCode.INVALID_JSON, (Object) e.getOriginalMessage());
        }
    }

    private List<JsonNode> extractRows(JsonNode root) {
        if (root.isObject()) {
            return List.of(root);
        }
        if (root.isArray()) {
            List<JsonNode> rows = new ArrayList<>();
            for (JsonNode element : root) {
                if (!element.isObject()) {
                    throw new BusinessException(DevUtilsErrorCode.INVALID_JSON,
                            (Object) "every array element must be a JSON object to convert to CSV");
                }
                rows.add(element);
            }
            return rows;
        }
        throw new BusinessException(DevUtilsErrorCode.INVALID_JSON,
                (Object) "expected a JSON array of objects, or a single JSON object, to convert to CSV");
    }

    private List<String> collectColumns(List<JsonNode> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            row.fieldNames().forEachRemaining(columns::add);
        }
        return new ArrayList<>(columns);
    }

    private List<Map<String, String>> buildRowMaps(List<JsonNode> rows, List<String> columns) {
        List<Map<String, String>> rowMaps = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (String column : columns) {
                rowMap.put(column, cellValue(row.get(column)));
            }
            rowMaps.add(rowMap);
        }
        return rowMaps;
    }

    private String cellValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
