package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.CartLineResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CartResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link Cart} (this module's own plain domain record — see its own Javadoc for why it isn't
 * a MapStruct target) to {@link CartResponse}.
 *
 * <p>Hand-written, not MapStruct — unlike every other mapper in this module, this one computes an
 * aggregate ({@code subtotal}/{@code itemCount} across only the {@code available} lines), which
 * doesn't fit MapStruct's per-field object-mapping model.
 */
@Component
public class CartMapper {

    public CartResponse toResponse(Cart cart) {
        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;

        List<CartLineResponse> resolvedLines = new ArrayList<>();
        for (CartLine line : cart.lines()) {
            CartLineResponse response = toLineResponse(line);
            if (line.available()) {
                subtotal = subtotal.add(response.getLineTotal());
                itemCount += line.quantity();
            }
            resolvedLines.add(response);
        }

        return CartResponse.builder()
                .lines(resolvedLines)
                .subtotal(subtotal)
                .itemCount(itemCount)
                .build();
    }

    /**
     * Maps one {@link CartLine} in isolation — extracted so {@code CheckoutMapper} can reuse the
     * exact same "unavailable → every field but variantId/quantity stays null" shape for the lines
     * it surfaces (checkout preview, and any lines silently dropped at confirm time) instead of
     * duplicating this branching.
     */
    public CartLineResponse toLineResponse(CartLine line) {
        CartLineResponse.CartLineResponseBuilder builder = CartLineResponse.builder()
                .variantId(line.variantId())
                .quantity(line.quantity())
                .available(line.available());
        if (line.available()) {
            ProductVariant variant = line.variant();
            Product product = variant.getProduct();
            BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            builder.sku(variant.getSku())
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSlug(product.getSlug())
                    .attributes(variant.getAttributes())
                    .unitPrice(variant.getPrice())
                    .lineTotal(lineTotal);
        }
        return builder.build();
    }
}
