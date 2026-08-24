package com.ttg.devknowledgeplatform.ecommerce.service;

import java.util.List;

/** A shopper's fully-resolved cart (US-2.3) — the list of {@link CartLine}s read from Redis, each joined live against the catalog. */
public record Cart(List<CartLine> lines) {
}
