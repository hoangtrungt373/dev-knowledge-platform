package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** REST response shape for one line of a confirmed order. {@code lineTotal} is derived, not stored — see {@code OrderLine}'s Javadoc. */
@Data
@Builder
public class OrderLineResponse {

    private Integer variantId;
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
    /**
     * The purchased variant's current attributes (e.g. {@code {"size": "15in", "color": "Black"}}),
     * primary product image, and product slug, all resolved live against today's catalog by
     * variant id — best-effort, unlike every other field on this response, which is a
     * frozen-at-purchase-time snapshot. All {@code null} if the variant (or its product) has since
     * been deleted — {@code OrderLine}'s own Javadoc explains why {@link #variantId} isn't a real
     * foreign key and can go stale like this. {@code productSlug} lets the GUI link a line back to
     * its product detail page; omitted (stays {@code null}) when there's nothing left to link to.
     */
    private Map<String, String> attributes;
    private String primaryImageUrl;
    private String productSlug;
}
