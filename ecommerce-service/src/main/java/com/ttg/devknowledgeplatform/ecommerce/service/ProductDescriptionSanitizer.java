package com.ttg.devknowledgeplatform.ecommerce.service;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

/**
 * Strips a {@link com.ttg.devknowledgeplatform.ecommerce.entity.Product#description}'s HTML down
 * to a small, safe allowlist before {@code ProductServiceImpl} ever persists it — the description
 * is authored through a WYSIWYG editor (`gui`'s planned {@code ProductDescriptionEditor}, backed by
 * TipTap) rather than typed as trusted markup, so it's treated the same as any other untrusted
 * HTML input regardless of who's logged in as the admin at the time.
 *
 * <p>Sanitized <strong>on write</strong> ({@code ProductServiceImpl.create}/{@code .update}), not
 * on read — so every consumer of {@code Product.description} (the public product-detail API, a
 * future admin preview, this module's own seeder) can render it directly without each one having
 * to remember to sanitize again. {@code gui}'s own read side still runs a client-side DOMPurify
 * pass as defense in depth, per the accepted plan for this feature — belt-and-suspenders, not a
 * substitute for this class.
 *
 * <p>The policy composes the library's own pre-built presets rather than hand-writing element/
 * attribute allowlists — {@link Sanitizers#BLOCKS} (p/headings/lists/blockquote/hr),
 * {@link Sanitizers#FORMATTING} (bold/italic/etc.), {@link Sanitizers#LINKS} ({@code a[href]},
 * safe-protocol-only), {@link Sanitizers#IMAGES} ({@code img[src][alt]}), and
 * {@link Sanitizers#TABLES}. Deliberately <strong>silent stripping</strong>, not a rejection — a
 * WYSIWYG editor can produce (or an admin can paste from Word/Google Docs) plenty of markup outside
 * this allowlist as a matter of course, and erroring on every stray tag would make the editor
 * unusable; only content that survives the allowlist is ever stored.
 *
 * @author ttg
 */
@Component
public class ProductDescriptionSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.BLOCKS
            .and(Sanitizers.FORMATTING)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.TABLES);

    /**
     * Sanitizes {@code rawHtml} down to the allowlisted subset described in this class's Javadoc.
     *
     * @param rawHtml the WYSIWYG-editor-authored HTML, or {@code null}
     * @return the sanitized HTML, or {@code null} if {@code rawHtml} was {@code null}
     */
    public String sanitize(String rawHtml) {
        return rawHtml == null ? null : POLICY.sanitize(rawHtml);
    }
}
