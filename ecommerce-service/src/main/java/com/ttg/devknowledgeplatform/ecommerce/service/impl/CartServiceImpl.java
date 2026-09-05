package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;
import com.ttg.devknowledgeplatform.ecommerce.service.CartService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link CartService} — Redis is the primary store here (not a cache), per
 * {@code docs/user-stories/02-cart-checkout.md}'s locked decision for this epic.
 *
 * <p>One Redis hash per cart, key {@code cart:{userUuid}}, fields {@code variantId -> quantity}
 * (both stored as plain strings via {@link StringRedisTemplate} — a hash is the natural fit for
 * "a handful of line items," and keeps the whole cart in one round trip). {@link #cartTtl} is a
 * sliding expiry, refreshed on every mutation (never on a plain read) — an abandoned cart quietly
 * disappearing after its configured TTL of inactivity is the intended behavior (US-2.4), not a bug
 * to guard against. Externalized via {@code app.ecommerce.cart.ttl} (default 30 days) rather than
 * a hardcoded constant, same convention {@code OutboxRelay}'s own
 * {@code app.ecommerce.outbox.relay.poll-interval} already established for a timing knob.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX = "cart:";

    private final StringRedisTemplate redisTemplate;
    private final ProductVariantRepository productVariantRepository;

    @Value("${app.ecommerce.cart.ttl:P30D}")
    private Duration cartTtl;

    @Override
    public void addItem(String userUuid, Integer variantId, int quantity) {
        validateVariantAvailable(variantId);
        String key = cartKey(userUuid);
        redisTemplate.opsForHash().increment(key, variantId.toString(), quantity);
        redisTemplate.expire(key, cartTtl);
        log.info("Added variantId={} quantity={} to cart for userUuid={}", variantId, quantity, userUuid);
    }

    @Override
    public void setQuantity(String userUuid, Integer variantId, int quantity) {
        String key = cartKey(userUuid);
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(key, variantId.toString());
            log.info("Removed variantId={} from cart for userUuid={}", variantId, userUuid);
        } else {
            validateVariantAvailable(variantId);
            redisTemplate.opsForHash().put(key, variantId.toString(), String.valueOf(quantity));
            log.info("Set variantId={} quantity={} in cart for userUuid={}", variantId, quantity, userUuid);
        }
        // Refreshed unconditionally, including on removal — any mutation counts as activity
        // (US-2.4), and EXPIRE on a key that just became empty (Redis drops an emptied hash on
        // its own) is a harmless no-op.
        redisTemplate.expire(key, cartTtl);
    }

    @Override
    public Cart getCart(String userUuid) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(cartKey(userUuid));
        if (raw.isEmpty()) {
            return new Cart(List.of());
        }
        // Bug fix: this used to call productVariantRepository.findById(variantId) once per line
        // (plus a second, lazy-loaded query the instant anything read variant.getProduct()) — an
        // N+1 query pattern on the app's single hottest read path (every cart view *and* every
        // checkout preview/confirm call). One batch, fetch-joined query resolves every line's
        // variant+product in a single round trip regardless of cart size — see
        // ProductVariantRepository#findAllByIdWithProduct's own Javadoc.
        List<Integer> variantIds = raw.keySet().stream()
                .map(rawVariantId -> Integer.valueOf((String) rawVariantId))
                .toList();
        Map<Integer, ProductVariant> variantsById = productVariantRepository.findAllByIdWithProduct(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<CartLine> lines = new ArrayList<>();
        raw.forEach((rawVariantId, rawQuantity) -> {
            Integer variantId = Integer.valueOf((String) rawVariantId);
            int quantity = Integer.parseInt((String) rawQuantity);
            lines.add(resolveLine(variantsById.get(variantId), variantId, quantity));
        });
        return new Cart(lines);
    }

    @Override
    public void removeItems(String userUuid, List<Integer> variantIds) {
        String key = cartKey(userUuid);
        Object[] hashKeys = variantIds.stream().map(Object::toString).toArray();
        redisTemplate.opsForHash().delete(key, hashKeys);
        // Same "refresh unconditionally, even on a removal that empties the hash" reasoning as
        // setQuantity's removal branch above.
        redisTemplate.expire(key, cartTtl);
        log.info("Removed variantIds={} from cart for userUuid={}", variantIds, userUuid);
    }

    private CartLine resolveLine(ProductVariant variant, Integer variantId, int quantity) {
        if (variant == null || !variant.getProduct().isActive()) {
            return CartLine.unavailable(variantId, quantity);
        }
        return CartLine.available(variantId, quantity, variant);
    }

    /** Soft existence/active check only (US-2.1) — no stock reservation at add-to-cart time; that's Epic 3's concern. */
    private void validateVariantAvailable(Integer variantId) {
        ProductVariant variant = Validator.notFound(
                productVariantRepository.findById(variantId), EcommerceErrorCode.PRODUCT_VARIANT_NOT_FOUND, variantId);
        Validator.isTrue(variant.getProduct().isActive(), EcommerceErrorCode.CART_VARIANT_UNAVAILABLE, variantId);
    }

    private static String cartKey(String userUuid) {
        return CART_KEY_PREFIX + userUuid;
    }
}
