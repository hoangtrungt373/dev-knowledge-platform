package com.ttg.devknowledgeplatform.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductDescriptionSanitizer} — verifies the allowlist policy keeps what a
 * legitimate WYSIWYG-authored description needs and strips everything a malicious/messy paste
 * could introduce.
 */
class ProductDescriptionSanitizerTest {

    private final ProductDescriptionSanitizer sanitizer = new ProductDescriptionSanitizer();

    @Test
    void returnsNullForNullInput() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void preservesAllowlistedFormattingAndBlockTags() {
        String html = "<p>A <strong>classic</strong> tee for when your <em>code</em> comes up empty.</p>"
                + "<ul><li>100% combed cotton</li><li>Unisex fit</li></ul>";

        String result = sanitizer.sanitize(html);

        assertThat(result).contains("<p>", "<strong>classic</strong>", "<em>code</em>", "<ul>", "<li>");
    }

    @Test
    void preservesAllowlistedTablesAndImages() {
        String html = "<table><tr><td>Size</td><td>M</td></tr></table>"
                + "<img src=\"https://example.com/tee.png\" alt=\"tee\">";

        String result = sanitizer.sanitize(html);

        assertThat(result).contains("<table", "Size", "<img", "src=\"https://example.com/tee.png\"");
    }

    @Test
    void stripsScriptTagsEntirely() {
        String html = "<p>Hello</p><script>alert('xss')</script>";

        String result = sanitizer.sanitize(html);

        assertThat(result).doesNotContain("<script", "alert");
        assertThat(result).contains("Hello");
    }

    @Test
    void stripsEventHandlerAttributes() {
        String html = "<p onclick=\"alert('xss')\">Click me</p>";

        String result = sanitizer.sanitize(html);

        assertThat(result).doesNotContain("onclick", "alert");
        assertThat(result).contains("Click me");
    }

    @Test
    void stripsJavascriptProtocolLinks() {
        String html = "<a href=\"javascript:alert('xss')\">click</a>";

        String result = sanitizer.sanitize(html);

        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    void preservesSafeLinks() {
        String html = "<a href=\"https://example.com\">example</a>";

        String result = sanitizer.sanitize(html);

        assertThat(result).contains("href=\"https://example.com\"", "example");
    }

    @Test
    void plainTextWithNoMarkupPassesThroughUnchanged() {
        String plain = "A classic tee for when your code comes up empty.";

        assertThat(sanitizer.sanitize(plain)).isEqualTo(plain);
    }

    /**
     * A realistic "pasted from Google Docs" paste — verbose {@code style=}-heavy markup, the
     * `docs-internal-guid` wrapper `<b>` Docs always adds, and one of Docs' own {@code /url?q=...}
     * link-redirector URLs. Documents two real, non-obvious effects of this allowlist (neither is a
     * bug — both are traced/explained where this class is used): (1) Docs represents bold via
     * inline {@code style="font-weight:700"} on a bare {@code <span>}, not a semantic {@code <b>}/
     * {@code <strong>} — since {@code STYLES} isn't in the composed policy and this version's
     * {@code FORMATTING} preset doesn't allow a bare {@code <span>} either, that "bold" signal is
     * lost entirely (the word survives, the boldness doesn't); (2) Docs' own wrapper {@code <b
     * style="font-weight:normal;">} exists specifically to *cancel* the semantic bold {@code <b>}
     * would otherwise imply — stripping only the {@code style} and keeping the {@code <b>} inverts
     * that, so the sanitized output would visually render as "everything bold," which the original
     * paste never intended. A real example of sanitization trading fidelity for safety, not just a
     * security filter.
     */
    @Test
    void sanitizesAMessyGoogleDocsPasteDroppingStylesAndTrackingWrapper() {
        String googleDocsPaste =
            "<meta charset=\"utf-8\">"
            + "<b id=\"docs-internal-guid-abc123\" style=\"font-weight:normal;\">"
            + "<p dir=\"ltr\" style=\"line-height:1.38;margin-top:0pt;margin-bottom:0pt;\">"
            + "<span style=\"font-size:11pt;font-family:Arial;color:#000000;\">"
            + "A classic tee for when your </span>"
            + "<span style=\"font-weight:700;\">code</span>"
            + "<span style=\"font-size:11pt;\"> comes up empty.</span></p>"
            + "<ul style=\"margin-top:0;margin-bottom:0;padding-inline-start:48px;\">"
            + "<li dir=\"ltr\" style=\"list-style-type:disc;\">"
            + "<span style=\"font-size:11pt;\">100% combed cotton</span></li>"
            + "<li dir=\"ltr\" style=\"list-style-type:disc;\">"
            + "<span style=\"font-size:11pt;\">Unisex fit</span></li></ul>"
            + "<p><a href=\"https://www.google.com/url?q=https://example.com/size-guide&amp;sa=D\">"
            + "size guide</a></p></b>";

        String result = sanitizer.sanitize(googleDocsPaste);

        // Verified against the real sanitizer output:
        // <b><p>A classic tee for when your code comes up empty.</p><ul><li>100% combed
        // cotton</li><li>Unisex fit</li></ul><p><a href="https://www.google.com/url?q&#61;
        // https://example.com/size-guide&amp;sa&#61;D" rel="nofollow">size guide</a></p></b>
        assertThat(result)
                .doesNotContain("style=", "<meta", "docs-internal-guid", "dir=", "<span")
                .contains(
                        "<p>A classic tee for when your code comes up empty.</p>",
                        "<ul><li>100% combed cotton</li><li>Unisex fit</li></ul>",
                        "size guide",
                        // LINKS' requireRelNofollowOnLinks() stamps this on every surviving link.
                        "rel=\"nofollow\"");
    }

    /**
     * Documents which block-level tags this policy accepts vs. silently drops — the reference
     * `gui`'s TipTap toolbar (`ProductDescriptionEditor.tsx`) was designed against, so an admin
     * never sees a formatting option that gets thrown away by the time it's persisted. `<hr>` is
     * fully dropped (no bare replacement, unlike an unlisted inline element); `<pre>` degrades to a
     * bare inline `<code>` (block-level code formatting is lost) — both are why StarterKit's
     * `horizontalRule`/`codeBlock` nodes are disabled in that component rather than offered and
     * silently misbehaving.
     */
    @Test
    void dropsHorizontalRuleAndDegradesCodeBlockToInlineCode() {
        assertThat(sanitizer.sanitize("<hr>")).isEmpty();
        assertThat(sanitizer.sanitize("<pre><code>const x = 1;</code></pre>"))
                .doesNotContain("<pre")
                .contains("<code>");
    }

    @Test
    void preservesHeadingsUnderlineAndBlockquote() {
        assertThat(sanitizer.sanitize("<h2>Heading</h2>")).isEqualTo("<h2>Heading</h2>");
        assertThat(sanitizer.sanitize("<u>underline</u>")).isEqualTo("<u>underline</u>");
        assertThat(sanitizer.sanitize("<blockquote>quoted</blockquote>")).isEqualTo("<blockquote>quoted</blockquote>");
    }
}
