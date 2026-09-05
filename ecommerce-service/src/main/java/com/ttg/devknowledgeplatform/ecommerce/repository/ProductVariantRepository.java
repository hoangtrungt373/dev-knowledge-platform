package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {

    boolean existsBySku(String sku);

    /** Excludes {@code id} from the uniqueness check — used when updating a variant that keeps its
     * own existing SKU (would otherwise "conflict" with itself). */
    boolean existsBySkuAndIdNot(String sku, Integer id);

    List<ProductVariant> findByProductId(Integer productId);

    /**
     * Batch-resolves every variant in {@code ids} in one round trip, eagerly fetching each one's
     * own (otherwise {@code LAZY}) {@code product} association in the same query — a plain
     * {@code findAllById} would still trigger one extra lazy-load query per variant the instant
     * something reads {@code variant.getProduct()}. Written specifically for
     * {@code service.impl.CartServiceImpl#getCart}, which used to call plain {@code findById} once
     * per cart line (an N+1 query pattern on the app's hottest read path — every cart view *and*
     * every checkout {@code preview}/{@code confirm} call); this turns an N-line cart into exactly
     * one query regardless of line count.
     */
    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.id IN :ids")
    List<ProductVariant> findAllByIdWithProduct(@Param("ids") Collection<Integer> ids);

    /**
     * Atomically reserves {@code quantity} units of a variant for an order (US-3.1) — the same
     * claim-style conditional {@code UPDATE} shape as {@code OutboxEventRepository.claim}: the
     * {@code WHERE} clause re-checks {@code stockQuantity - reservedQuantity >= :quantity} in the
     * same statement as the increment, so two checkouts racing the same variant can never both
     * succeed past the available stock the way a separate read-then-write could under Postgres's
     * default READ COMMITTED isolation.
     *
     * @return {@code 1} if the reservation succeeded, {@code 0} if not enough stock was available —
     *         the caller (checkout, US-3.1) must roll back the whole order transaction on {@code 0}
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.reservedQuantity = v.reservedQuantity + :quantity "
            + "WHERE v.id = :variantId AND (v.stockQuantity - v.reservedQuantity) >= :quantity")
    int reserve(@Param("variantId") Integer variantId, @Param("quantity") Integer quantity);

    /**
     * Releases a previously-reserved quantity without touching {@code stockQuantity} — the
     * compensating action for a reservation that never became a sale: an expired order (US-3.2), a
     * declined payment (US-3.3), or a cancellation before payment succeeded (US-3.6).
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.reservedQuantity = v.reservedQuantity - :quantity WHERE v.id = :variantId")
    int release(@Param("variantId") Integer variantId, @Param("quantity") Integer quantity);

    /**
     * Converts a reservation into a real sale once payment is confirmed (US-3.3): decrements
     * {@code stockQuantity} and {@code reservedQuantity} together, per the two-column reservation
     * model in {@code docs/user-stories/03-order-lifecycle-inventory.md} — a released reservation
     * only ever touches {@code reservedQuantity} alone (see {@link #release}), but a confirmed sale
     * must also remove the unit from stock for good.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity - :quantity, "
            + "v.reservedQuantity = v.reservedQuantity - :quantity WHERE v.id = :variantId")
    int confirmSale(@Param("variantId") Integer variantId, @Param("quantity") Integer quantity);

    /**
     * Restocks a quantity that was already sold (US-3.6's {@code CONFIRMED -> CANCELLED} path,
     * i.e. cancelling after {@link #confirmSale} already ran) — increments {@code stockQuantity}
     * only, the mirror image of {@link #confirmSale}: that reservation no longer exists to release,
     * only the stock itself needs giving back.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :quantity WHERE v.id = :variantId")
    int restock(@Param("variantId") Integer variantId, @Param("quantity") Integer quantity);
}
