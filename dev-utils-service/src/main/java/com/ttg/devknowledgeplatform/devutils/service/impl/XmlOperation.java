package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

/**
 * Validates a raw XML string and re-serializes it indented (or, with {@code minify}, with
 * insignificant inter-element whitespace stripped) — the one new operation in this batch backed by
 * a real grammar parser (JAXP, built into the JDK — no new Maven dependency needed), the same
 * "real parse, real invalid-input error" shape {@code JsonFormatOperation}/{@code
 * YamlToJsonOperation} already establish, rather than the lenient brace-based reformatting
 * {@code CssOperation}/{@code LessOperation}/{@code ScssOperation}/{@code JsOperation} use (see
 * {@code service.impl.support.CurlyBraceFormatter}'s own Javadoc for why XML gets this treatment
 * and those four don't: XML has one well-defined grammar a JDK-bundled parser already implements,
 * unlike CSS/LESS/SCSS/JS which don't share one).
 *
 * <p><b>XXE hardening is not optional here.</b> This is one of the fully public, unauthenticated
 * endpoints in this reactor (see this module's own {@code CLAUDE.md}) — a {@link
 * DocumentBuilderFactory} left at its JDK defaults will happily resolve a {@code <!DOCTYPE>}
 * declaration's external entities, a textbook XXE (XML External Entity) injection vector: a
 * malicious caller could submit a document whose DTD references a local file or an internal network
 * URL and have this service read it back in the "beautified" output. Both the {@link
 * DocumentBuilderFactory} and the {@link TransformerFactory} below are hardened per the OWASP XXE
 * Prevention Cheat Sheet's JAXP baseline: {@code <!DOCTYPE>} is disallowed outright (the simplest,
 * most robust defense — this operation has no legitimate use for a DTD anyway), external general/
 * parameter entities and external DTD loading are disabled as defense in depth, and the
 * {@link TransformerFactory} used to serialize the result has external DTD/stylesheet access
 * disabled too.
 *
 * <p>Whitespace-only text nodes are stripped from the parsed DOM before serializing either way —
 * without this, {@link Transformer}'s own indent mode would double up on whatever whitespace the
 * source already had between elements, and minify would have nothing removed to actually compact.
 * A text node that contains real (non-blank) content — the whole point of an XML text node — is
 * never touched, whitespace-only or not: this only removes nodes that are *entirely* whitespace.
 */
@Component
public class XmlOperation implements DevUtilOperation {

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_XML} when {@code input}
     *                           isn't well-formed XML
     */
    public String execute(String input, boolean minify) {
        try {
            Document document = parse(input);
            stripWhitespaceOnlyTextNodes(document.getDocumentElement());
            return render(document, input, minify);
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            // (Object) is load-bearing here too — see JsonFormatOperation's own comment on the
            // identical cast for the full reasoning (a plain String argument would silently skip
            // DevUtilsErrorCode#formatMessage(), never applying the "Invalid XML: {0}" template).
            throw new BusinessException(DevUtilsErrorCode.INVALID_XML, (Object) friendlyMessage(e));
        }
    }

    private Document parse(String input) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // OWASP XXE-prevention baseline for JAXP — see this class's own Javadoc.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        var builder = factory.newDocumentBuilder();
        // The JDK's default handler prints every warning/error/fatal error straight to stderr —
        // fine for a rare, unexpected failure, but this endpoint is fully public with no
        // authentication (see this module's own CLAUDE.md), so a malformed submission is routine,
        // expected input, not something worth spamming server logs over. Still rethrows on
        // error/fatalError (same as the default behavior) — only the console noise is suppressed.
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        return builder.parse(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    private void stripWhitespaceOnlyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripWhitespaceOnlyTextNodes(child);
            }
        }
    }

    private String render(Document document, String originalInput, boolean minify) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        // Preserve the caller's own choice to include (or omit) an XML declaration rather than
        // always adding or always dropping one, so round-tripping a document that had no
        // declaration doesn't silently gain one.
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
                originalInput.stripLeading().startsWith("<?xml") ? "no" : "yes");
        if (minify) {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString().strip();
    }

    /** Turns a JAXP parse/transform failure into a clean, single-line message — for a
     * {@link SAXParseException} (the common case, a real XML syntax error), this appends the exact
     * {@code (line N, column M)} location, the same convention {@code ParsingExceptionMessages}
     * already establishes for the JSON/YAML operations. */
    private String friendlyMessage(Exception e) {
        if (e instanceof SAXParseException spe) {
            int line = spe.getLineNumber();
            int column = spe.getColumnNumber();
            String location = line >= 0 ? " (line " + line + ", column " + column + ")" : "";
            return spe.getMessage() + location;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
