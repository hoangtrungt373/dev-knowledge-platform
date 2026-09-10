package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

import lombok.RequiredArgsConstructor;

/**
 * Parses a URL into its structural components — {@code protocol}/{@code username}/
 * {@code password}/{@code hostname}/{@code port}/{@code pathname}/{@code search}/{@code hash}/
 * {@code origin}, deliberately named and shaped after the browser's own {@code URL} object (the
 * WHATWG URL Standard) rather than {@code java.net.URI}'s own accessor names — plus one field
 * that object doesn't have, {@code query}, the parsed {@code search} string as a real JSON object
 * (what most "URL parser" tools add on top of the raw property list). The first operation to
 * actually declare {@link OperationGroup#WEB} — a URL is exactly the "inherently web-specific
 * concept, not a generic text shape" that group's own Javadoc describes (its own named example was
 * an HTTP header/user-agent parser, still unbuilt; this is the same spirit, not that literal
 * example).
 *
 * <p><b>Backed by {@link URI}, a real validating parser — not a hand-rolled string split — but
 * genuinely not identical to a browser's own WHATWG URL parser</b>, which this operation's field
 * names otherwise closely mirror. {@link URI} is stricter about what it accepts (no unencoded
 * spaces or several other characters a browser tolerates) and normalizes less (it preserves the
 * scheme/host's original casing, where the WHATWG parser always lowercases both) — this method
 * closes the casing gap explicitly ({@link String#toLowerCase(Locale)} on both), but does not
 * attempt to relax {@link URI}'s own stricter syntax acceptance to match a browser's leniency.
 * Rejecting a URL a browser would happily accept is an accepted, documented gap, not a bug to
 * chase — the same "real parser, not spec-identical to what these field names evoke" trade-off
 * {@code RegexTesterOperation}'s own JS-flags-on-a-Java-engine translation already makes.
 *
 * <p><b>{@code port} and {@code origin} both normalize away a scheme's own default port</b> —
 * {@code https} on port 443, {@code http} on 80, {@code ws} on 80, {@code wss} on 443, {@code ftp}
 * on 21 (the WHATWG Standard's fixed "special scheme" table) — matching every browser's own
 * {@code URL.port}/{@code URL.origin}, which both treat an explicitly-default port exactly the
 * same as an absent one. {@code origin} is the literal string {@code "null"} for any other scheme
 * (matching the WHATWG Standard's own opaque-origin serialization for e.g. {@code mailto:}/
 * {@code data:}/{@code file:}), not a JSON {@code null} — this operation's whole output is a JSON
 * <i>object</i>, and a bare {@code null} value there would read as "this field is simply absent,"
 * which isn't what an opaque origin means.
 *
 * <p><b>{@code query} parses {@code search} into a plain key→value JSON object</b>
 * (percent-decoding both key and value via {@link URLDecoder#decode(String, java.nio.charset.Charset)},
 * the same application/x-www-form-urlencoded convention {@code UrlDecodeOperation} already uses
 * elsewhere in this module) — matching {@code Object.fromEntries(new URLSearchParams(search))}'s
 * own behavior in JS, including for a duplicate key: the <i>last</i> occurrence wins, but the key
 * keeps its <i>first</i> position in the object (a plain insertion-ordered map update, not a
 * remove-then-re-append) — the same behavior a JS object's own property reassignment already has.
 * A query key with no {@code '='} (e.g. {@code ?flag}) gets an empty-string value.
 */
@Component
@RequiredArgsConstructor
public class UrlParserOperation implements DevUtilOperation {

    // The WHATWG URL Standard's own fixed "special scheme" default-port table.
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
            "ftp", 21,
            "http", 80,
            "https", 443,
            "ws", 80,
            "wss", 443);

    // Schemes with a real network origin ("scheme://host[:port]"); every other scheme's own
    // `origin` is the opaque literal "null" — see this class's own Javadoc.
    private static final Set<String> NETWORK_SCHEMES = Set.of("ftp", "http", "https", "ws", "wss");

    private final ObjectMapper objectMapper;

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_URL} when {@code input}
     *                           isn't syntactically a valid URI, or is a valid URI reference that
     *                           isn't absolute (no scheme) or has no host (e.g. {@code mailto:}
     *                           addresses, which are valid URIs but have neither)
     */
    public String execute(String input, boolean minify) {
        URI uri;
        try {
            uri = new URI(input.strip());
        } catch (URISyntaxException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_URL, (Object) e.getMessage());
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_URL, (Object) (
                    "must be an absolute URL with a scheme and host"));
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String hostname = uri.getHost().toLowerCase(Locale.ROOT);
        String[] userInfo = splitUserInfo(uri.getUserInfo());
        String port = resolvePort(scheme, uri.getPort());
        String rawPath = uri.getRawPath();
        String pathname = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        String search = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String hash = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocol", scheme + ":");
        result.put("username", userInfo[0]);
        result.put("password", userInfo[1]);
        result.put("hostname", hostname);
        result.put("port", port);
        result.put("pathname", pathname);
        result.put("search", search);
        result.set("query", parseQuery(uri.getRawQuery()));
        result.put("hash", hash);
        result.put("origin", buildOrigin(scheme, hostname, port));

        return JsonNodeIo.write(objectMapper, result, minify, DevUtilsErrorCode.INVALID_URL);
    }

    private static String[] splitUserInfo(String userInfo) {
        if (userInfo == null || userInfo.isEmpty()) {
            return new String[] {"", ""};
        }
        int colon = userInfo.indexOf(':');
        return colon < 0
                ? new String[] {userInfo, ""}
                : new String[] {userInfo.substring(0, colon), userInfo.substring(colon + 1)};
    }

    private static String resolvePort(String scheme, int port) {
        if (port < 0) {
            return "";
        }
        Integer defaultPort = DEFAULT_PORTS.get(scheme);
        return defaultPort != null && defaultPort == port ? "" : String.valueOf(port);
    }

    private static String buildOrigin(String scheme, String hostname, String port) {
        if (!NETWORK_SCHEMES.contains(scheme)) {
            return "null";
        }
        return scheme + "://" + hostname + (port.isEmpty() ? "" : ":" + port);
    }

    private ObjectNode parseQuery(String rawQuery) {
        ObjectNode query = objectMapper.createObjectNode();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq < 0 ? pair : pair.substring(0, eq);
            String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
            query.put(decode(rawKey), decode(rawValue));
        }
        return query;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
