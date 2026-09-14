package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.ParseSettings;
import org.jsoup.parser.Parser;

/**
 * Converts raw HTML into JSX-flavored markup text, branded as TSX (the operation's own public
 * name is "HTML to TSX", per direct request) — {@code class}→{@code className},
 * {@code for}→{@code htmlFor}, every other HTML attribute whose JSX/DOM-property name differs
 * from its HTML name (a fixed lookup table, {@link #ATTRIBUTE_NAME_MAP}), an inline
 * {@code style="..."} string→a real JS object literal ({@code style={{ prop: "value" }}}, CSS
 * property names camelCased), an {@code on*} event-handler attribute→its camelCase JSX name (e.g.
 * {@code onclick}→{@code onClick}, {@code onmouseover}→{@code onMouseOver}), and every void
 * element ({@code <br>}, {@code <img>}, {@code <input>}, ...) force-closed with a trailing
 * {@code />}, since JSX (unlike HTML) has no notion of a void element that can be left
 * syntactically unclosed. {@code data-*}/{@code aria-*} attributes are deliberately left
 * untouched — React/JSX keeps both kebab-case, unlike every other HTML attribute.
 *
 * <p><b>"TSX" vs. "JSX" here is a naming/file-extension choice, not a different output shape</b> —
 * TSX is just JSX's own file extension convention once a project is TypeScript (a {@code .tsx}
 * file's syntax rules for markup are identical to a {@code .jsx} file's); this converter never adds
 * type annotations, interfaces, or generics, since it only ever sees a markup fragment with no
 * props/component boundary to type in the first place. The output text is therefore valid,
 * unchanged, in either a {@code .tsx} or a {@code .jsx} file — this class (and its own Javadoc
 * below) still says "JSX" wherever the prose is actually about JSX syntax mechanics, since that's
 * the accurate term for what's being described; only the operation's own public name/label/file
 * extension is TSX-branded.
 *
 * <p><b>Why this needs its own serialization pass, not just jsoup's stock HTML output</b> — four
 * separate jsoup behaviors this class works around, each confirmed empirically against the actual
 * resolved jsoup 1.17.2 jar via a standalone harness before relying on it, not assumed:
 * <ol>
 *   <li>jsoup's default {@link Parser#htmlParser()} (and {@link Jsoup#parseBodyFragment}) lowercases
 *       every attribute key — including one freshly set via {@link Element#attr(String, String)}
 *       <em>after</em> parsing, not just the original attributes as typed — because {@code .attr()}
 *       itself re-normalizes the key through the owning document's own {@link ParseSettings}.
 *       {@code className}/{@code htmlFor}/{@code tabIndex} would silently come back as
 *       {@code classname}/{@code htmlfor}/{@code tabindex} without parsing with
 *       {@link ParseSettings#preserveCase} instead — confirmed both directions (default vs.
 *       {@code preserveCase}) via the harness before writing this class.
 *   <li>Neither jsoup output {@link Document.OutputSettings.Syntax} self-closes what we actually
 *       need self-closed: {@code html} syntax never emits a self-closing {@code />} for a void
 *       element (correct, standard HTML5 serialization — the browser doesn't need one), and
 *       {@code xml} syntax renders a boolean-style attribute (e.g. bare {@code disabled}) as
 *       {@code disabled=""} instead of the bare JSX-shorthand form (XML has no boolean-attribute
 *       concept) — confirmed via the harness that neither single {@code Syntax} value satisfies
 *       both requirements at once, so {@code html} syntax (correct boolean attributes) is used for
 *       the base serialization, and void-element self-closing is added back via a targeted regex
 *       post-process instead ({@link #VOID_TAG_PATTERN}).
 *   <li>There is no jsoup {@link Attribute} shape for "an unquoted, raw JS object literal value" —
 *       every {@link Element#attr(String, String)} value is always wrapped in double quotes and
 *       HTML-escaped on output. The {@code style} object is instead written as a normal (quoted,
 *       escaped) attribute value first, then unwrapped/unescaped by a second targeted regex
 *       post-process ({@link #STYLE_ATTR_PATTERN}) once serialization is done.
 *   <li>jsoup's own pretty-printer only breaks a <em>block</em>-level tag onto its own line
 *       ({@code Tag.isBlock()}/{@code isInline()} — the same HTML5 categorization a browser uses to
 *       decide default {@code display}) — plain {@code prettyPrint(true)} alone still renders two
 *       inline tags like {@code <label>}/{@code <input>} on one single line, a real bug reported
 *       directly ("the result is inline even though we do not choose Minify"): correct for
 *       rendering HTML in a browser, but not what a human reading generated JSX/TSX source code
 *       wants, since JSX has no "inline element" concept to defer to. {@code OutputSettings.outline
 *       (true)} forces every element onto its own indented line regardless of its HTML
 *       inline/block category — confirmed via the harness before relying on it, the same way as
 *       the other 3 quirks above.
 * </ol>
 *
 * <p><b>Known, deliberate limitations, not chased further</b> (same "lenient, best-effort,
 * documented gap" trade-off {@code CurlyBraceFormatter}/{@code SqlFormatter} already establish for
 * their own textual reformatters): an attribute value containing a literal {@code &} or {@code "}
 * comes back HTML-entity-escaped ({@code &amp;}/{@code &quot;}) by jsoup's own serializer, not
 * unescaped into the literal character a real JS string would use — correct HTML, but not
 * byte-for-byte what a human would type directly into a {@code .jsx} file for that rare case. Only
 * {@code style}'s own object-literal value is unescaped, since that one is mechanically required to
 * produce syntactically valid JSX at all (a raw {@code &quot;-quoted} string wouldn't even parse
 * as JS). An {@code on*} attribute name not in {@link #EVENT_NAME_MAP} falls back to a generic
 * "capitalize the letter right after {@code on}" heuristic, which is only actually correct for a
 * single-word event name (a genuinely unknown multi-word DOM event would need its own map entry).
 * SVG's own distinct camelCased attribute set (e.g. {@code viewBox}) is not covered — this table is
 * HTML-attribute-focused. A bare top-level comment with no other real markup around it (e.g. the
 * literal input {@code <!-- note -->} alone) is lost entirely — jsoup's own HTML tree builder
 * attaches a comment encountered before any real content as a sibling of {@code <html>}, never
 * inside {@code <body>}, so {@link Element#html()} on the body never sees it; a comment nested
 * inside real markup (the overwhelmingly common real-world shape) converts correctly. Never
 * throws — jsoup's parser is deliberately lenient and always produces a best-effort DOM, the same
 * shape {@code HtmlBeautifyOperation}/{@code HtmlSanitizer} already establish.
 */
public final class HtmlToTsxConverter {

    private static final Map<String, String> ATTRIBUTE_NAME_MAP = Map.ofEntries(
            Map.entry("class", "className"),
            Map.entry("for", "htmlFor"),
            Map.entry("accept-charset", "acceptCharset"),
            Map.entry("accesskey", "accessKey"),
            Map.entry("allowfullscreen", "allowFullScreen"),
            Map.entry("autocapitalize", "autoCapitalize"),
            Map.entry("autocomplete", "autoComplete"),
            Map.entry("autocorrect", "autoCorrect"),
            Map.entry("autofocus", "autoFocus"),
            Map.entry("autoplay", "autoPlay"),
            Map.entry("autosave", "autoSave"),
            Map.entry("cellpadding", "cellPadding"),
            Map.entry("cellspacing", "cellSpacing"),
            Map.entry("charset", "charSet"),
            Map.entry("classid", "classID"),
            Map.entry("colspan", "colSpan"),
            Map.entry("contenteditable", "contentEditable"),
            Map.entry("contextmenu", "contextMenu"),
            Map.entry("controlslist", "controlsList"),
            Map.entry("crossorigin", "crossOrigin"),
            Map.entry("datetime", "dateTime"),
            Map.entry("enctype", "encType"),
            Map.entry("formaction", "formAction"),
            Map.entry("formenctype", "formEncType"),
            Map.entry("formmethod", "formMethod"),
            Map.entry("formnovalidate", "formNoValidate"),
            Map.entry("formtarget", "formTarget"),
            Map.entry("frameborder", "frameBorder"),
            Map.entry("hreflang", "hrefLang"),
            Map.entry("http-equiv", "httpEquiv"),
            Map.entry("httpequiv", "httpEquiv"),
            Map.entry("inputmode", "inputMode"),
            Map.entry("itemid", "itemID"),
            Map.entry("itemprop", "itemProp"),
            Map.entry("itemref", "itemRef"),
            Map.entry("itemscope", "itemScope"),
            Map.entry("itemtype", "itemType"),
            Map.entry("keyparams", "keyParams"),
            Map.entry("keytype", "keyType"),
            Map.entry("marginheight", "marginHeight"),
            Map.entry("marginwidth", "marginWidth"),
            Map.entry("maxlength", "maxLength"),
            Map.entry("mediagroup", "mediaGroup"),
            Map.entry("minlength", "minLength"),
            Map.entry("nomodule", "noModule"),
            Map.entry("novalidate", "noValidate"),
            Map.entry("radiogroup", "radioGroup"),
            Map.entry("readonly", "readOnly"),
            Map.entry("referrerpolicy", "referrerPolicy"),
            Map.entry("rowspan", "rowSpan"),
            Map.entry("spellcheck", "spellCheck"),
            Map.entry("srcdoc", "srcDoc"),
            Map.entry("srclang", "srcLang"),
            Map.entry("srcset", "srcSet"),
            Map.entry("tabindex", "tabIndex"),
            Map.entry("usemap", "useMap"));

    private static final Map<String, String> EVENT_NAME_MAP = Map.ofEntries(
            Map.entry("onclick", "onClick"), Map.entry("ondblclick", "onDoubleClick"),
            Map.entry("onmousedown", "onMouseDown"), Map.entry("onmouseenter", "onMouseEnter"),
            Map.entry("onmouseleave", "onMouseLeave"), Map.entry("onmousemove", "onMouseMove"),
            Map.entry("onmouseout", "onMouseOut"), Map.entry("onmouseover", "onMouseOver"),
            Map.entry("onmouseup", "onMouseUp"), Map.entry("oncontextmenu", "onContextMenu"),
            Map.entry("onkeydown", "onKeyDown"), Map.entry("onkeypress", "onKeyPress"),
            Map.entry("onkeyup", "onKeyUp"), Map.entry("onfocus", "onFocus"),
            Map.entry("onblur", "onBlur"), Map.entry("onchange", "onChange"),
            Map.entry("oninput", "onInput"), Map.entry("oninvalid", "onInvalid"),
            Map.entry("onsubmit", "onSubmit"), Map.entry("onreset", "onReset"),
            Map.entry("onselect", "onSelect"), Map.entry("onload", "onLoad"),
            Map.entry("onerror", "onError"), Map.entry("onscroll", "onScroll"),
            Map.entry("onwheel", "onWheel"), Map.entry("oncopy", "onCopy"),
            Map.entry("oncut", "onCut"), Map.entry("onpaste", "onPaste"),
            Map.entry("ondrag", "onDrag"), Map.entry("ondragend", "onDragEnd"),
            Map.entry("ondragenter", "onDragEnter"), Map.entry("ondragexit", "onDragExit"),
            Map.entry("ondragleave", "onDragLeave"), Map.entry("ondragover", "onDragOver"),
            Map.entry("ondragstart", "onDragStart"), Map.entry("ondrop", "onDrop"),
            Map.entry("ontouchcancel", "onTouchCancel"), Map.entry("ontouchend", "onTouchEnd"),
            Map.entry("ontouchmove", "onTouchMove"), Map.entry("ontouchstart", "onTouchStart"),
            Map.entry("onanimationstart", "onAnimationStart"), Map.entry("onanimationend", "onAnimationEnd"),
            Map.entry("onanimationiteration", "onAnimationIteration"), Map.entry("ontransitionend", "onTransitionEnd"),
            Map.entry("onplay", "onPlay"), Map.entry("onpause", "onPause"),
            Map.entry("onended", "onEnded"), Map.entry("onvolumechange", "onVolumeChange"),
            Map.entry("onwaiting", "onWaiting"), Map.entry("oncanplay", "onCanPlay"),
            Map.entry("oncanplaythrough", "onCanPlayThrough"), Map.entry("ondurationchange", "onDurationChange"),
            Map.entry("onemptied", "onEmptied"), Map.entry("onloadeddata", "onLoadedData"),
            Map.entry("onloadedmetadata", "onLoadedMetadata"), Map.entry("onloadstart", "onLoadStart"),
            Map.entry("onplaying", "onPlaying"), Map.entry("onprogress", "onProgress"),
            Map.entry("onratechange", "onRateChange"), Map.entry("onseeked", "onSeeked"),
            Map.entry("onseeking", "onSeeking"), Map.entry("onstalled", "onStalled"),
            Map.entry("onsuspend", "onSuspend"), Map.entry("ontimeupdate", "onTimeUpdate"),
            Map.entry("ontoggle", "onToggle"));

    private static final List<String> VOID_TAGS = List.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track",
            "wbr");

    private static final Pattern STYLE_ATTR_PATTERN = Pattern.compile("style=\"(\\{\\{[^\"]*}})\"");
    private static final Pattern VOID_TAG_PATTERN = Pattern.compile(
            "<(" + String.join("|", VOID_TAGS) + ")((?:\\s+[^<>]*)?)(?<!/)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);

    private HtmlToTsxConverter() {
    }

    /**
     * Converts {@code input} into JSX-flavored markup — see this class's own Javadoc for the full
     * attribute/style/void-element/event-handler rules and known limitations. Never throws.
     *
     * @param input  raw HTML to convert
     * @param minify {@code false} pretty-prints with 2-space indentation, one child per line
     *               ({@code OutputSettings.outline(true)} — see below); {@code true} produces
     *               jsoup's unformatted single-pass output instead
     * @return the JSX-flavored markup
     */
    public static String convert(String input, boolean minify) {
        Document document = Jsoup.parse(input, "", Parser.htmlParser().settings(ParseSettings.preserveCase));
        Element body = document.body();

        for (Element element : body.getAllElements()) {
            convertAttributes(element);
        }

        // outline(true) is load-bearing, not decorative — a real bug reported directly ("the
        // result is inline even though we do not choose Minify"). jsoup's own pretty-printer
        // defaults to HTML's inline-vs-block distinction (Tag.isBlock()/isInline()): `label` and
        // `input` are both inline tags, so jsoup keeps them on one line regardless of
        // prettyPrint(true) — correct behavior for rendering HTML in a browser, but wrong for this
        // operation's actual audience (a human reading generated JSX/TSX source, which has no
        // "inline element" concept at all). outline(true) forces every element onto its own
        // indented line unconditionally — confirmed via a standalone harness against the real
        // jsoup 1.17.2 jar before relying on it, the same "empirically confirm, don't assume"
        // discipline this class's own Javadoc already documents for its other 3 jsoup quirks.
        document.outputSettings()
                .prettyPrint(!minify)
                .outline(!minify)
                .indentAmount(2)
                .syntax(Document.OutputSettings.Syntax.html);
        String html = body.html();

        html = unwrapStyleObject(html);
        html = selfCloseVoidElements(html);
        html = convertComments(html);
        return html;
    }

    private static void convertAttributes(Element element) {
        List<Attribute> originalAttributes = new ArrayList<>(element.attributes().asList());
        element.clearAttributes();
        for (Attribute attribute : originalAttributes) {
            String htmlKey = attribute.getKey().toLowerCase();
            if (htmlKey.equals("style")) {
                element.attr("style", styleToJsxObjectLiteral(attribute.getValue()));
                continue;
            }
            String jsxKey = jsxAttributeName(htmlKey, attribute.getKey());
            if (attribute.hasDeclaredValue()) {
                element.attr(jsxKey, attribute.getValue());
            } else {
                // A bare boolean-style attribute (e.g. `disabled`, no `=`) — kept bare, the same
                // valid JSX shorthand for `disabled={true}`, rather than forcing a `="true"` value
                // no HTML author actually wrote.
                element.attr(jsxKey, true);
            }
        }
    }

    private static String jsxAttributeName(String lowercaseHtmlKey, String originalKey) {
        if (lowercaseHtmlKey.startsWith("on") && lowercaseHtmlKey.length() > 2) {
            return EVENT_NAME_MAP.getOrDefault(lowercaseHtmlKey, genericEventName(lowercaseHtmlKey));
        }
        // data-*/aria-* (and anything else not in the table, e.g. a custom attribute) pass through
        // exactly as originally cased — React/JSX keeps both of those kebab-case, unlike every
        // other HTML attribute this table renames.
        return ATTRIBUTE_NAME_MAP.getOrDefault(lowercaseHtmlKey, originalKey);
    }

    private static String genericEventName(String lowercaseOnAttribute) {
        // Best-effort fallback for an on* attribute this class doesn't otherwise recognize — only
        // actually correct for a single-word event name (e.g. onfoo -> onFoo); see this class's
        // own Javadoc for why a genuinely unknown multi-word DOM event needs its own map entry
        // instead.
        return "on" + Character.toUpperCase(lowercaseOnAttribute.charAt(2)) + lowercaseOnAttribute.substring(3);
    }

    private static String styleToJsxObjectLiteral(String css) {
        StringBuilder objectLiteral = new StringBuilder("{{ ");
        boolean first = true;
        for (String declaration : css.split(";")) {
            String trimmed = declaration.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String property = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            if (!first) {
                objectLiteral.append(", ");
            }
            objectLiteral.append(cssPropertyToJsCamelCase(property)).append(": \"").append(value).append('"');
            first = false;
        }
        objectLiteral.append(" }}");
        return objectLiteral.toString();
    }

    private static String cssPropertyToJsCamelCase(String cssProperty) {
        // A leading `--` custom property (e.g. `--main-color`) is kept fully verbatim, dashes
        // included — React's own style-object convention never camelCases a CSS custom property,
        // since its name is caller-defined, not one of the fixed CSSOM property names this
        // camelCasing rule is meant for.
        if (cssProperty.startsWith("--")) {
            return cssProperty;
        }
        StringBuilder camelCase = new StringBuilder();
        boolean upperNextChar = false;
        for (int i = 0; i < cssProperty.length(); i++) {
            char c = cssProperty.charAt(i);
            if (c == '-') {
                upperNextChar = true;
                continue;
            }
            camelCase.append(upperNextChar ? Character.toUpperCase(c) : c);
            upperNextChar = false;
        }
        return camelCase.toString();
    }

    private static String unwrapStyleObject(String html) {
        Matcher matcher = STYLE_ATTR_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String unescaped = matcher.group(1).replace("&quot;", "\"");
            matcher.appendReplacement(result, Matcher.quoteReplacement("style=" + unescaped));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String selfCloseVoidElements(String html) {
        Matcher matcher = VOID_TAG_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<" + matcher.group(1) + matcher.group(2) + " />"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String convertComments(String html) {
        Matcher matcher = HTML_COMMENT_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("{/*" + matcher.group(1) + "*/}"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
