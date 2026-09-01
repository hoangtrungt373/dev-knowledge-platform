package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRepository;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Seeds {@link Coupon} rows from {@code data/csv/coupons.csv} (columns: {@code code}, {@code
 * target}, {@code type}, {@code value}, {@code active}, {@code startAt}, {@code endAt}, {@code
 * minSubtotal}, {@code maxRedemptions}, {@code maxRedemptionsPerUser}, {@code maxDiscountAmount},
 * {@code description}) — a handful of realistic sample coupons (a welcome discount, a
 * min-subtotal-gated fixed discount, a redemption-capped percentage discount also capped by
 * {@code maxDiscountAmount} ({@code VIP20} — "20% off orders over $100, up to $20 off", the exact
 * shape this field was added for), two shipping-fee coupons — one that fully covers the flat fee,
 * one that only partially discounts it, demonstrating both branches of the GUI's "was $X, now Y"
 * shipping display — an active seasonal date-range promo, an already-expired one, and an inactive
 * one) covering every eligibility condition {@code CouponRedemptionService.resolve} checks, so the
 * checkout code-entry UI and the admin coupon list have something realistic to exercise against
 * without an admin manually creating one first. Every row also carries a real {@code description}
 * — the field a future {@code gui} coupon-picker dialog will show a shopper, so the seed data
 * doubles as a preview of what that dialog will actually render.
 *
 * <p><strong>Idempotency key is {@code code} itself</strong> — same simplification {@link
 * ProductTagSeeder}/{@link ProductCategorySeeder} make for the same reason (see their own
 * Javadoc): this is a small, fixed sample set, not long-lived production content. Unlike those two
 * seeders' {@code name}, {@code code} is already the entity's own real natural key ({@code Coupon}
 * enforces a database {@code UNIQUE} constraint on it) — normalized to uppercase before both the
 * existence check and persisting, mirroring {@code CouponServiceImpl.normalizeCode}'s own
 * behavior, so a re-run is idempotent even if this CSV's own casing were ever inconsistent.
 *
 * <p>Bypasses {@code CouponService} and persists directly via the repository, mirroring {@code
 * ProductTagSeeder} rather than {@code ProductSeeder} — creating a {@code Coupon} has no outbox
 * event/read-model side effect that would require going through the service layer.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class CouponSeeder extends CsvSeeder<Coupon> {

    private final CouponRepository couponRepository;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/coupons.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        return couponRepository.existsByCode(normalizeCode(record));
    }

    @Override
    protected Coupon buildEntity(CSVRecord record) {
        Coupon coupon = new Coupon();
        coupon.setCode(normalizeCode(record));
        coupon.setTarget(CouponTarget.valueOf(record.get("target").trim()));
        coupon.setType(CouponType.valueOf(record.get("type").trim()));
        coupon.setValue(new BigDecimal(record.get("value").trim()));
        coupon.setActive(parseActive(record));
        coupon.setStartAt(parseInstant(record.get("startAt")));
        coupon.setEndAt(parseInstant(record.get("endAt")));
        coupon.setMinSubtotal(parseBigDecimal(record.get("minSubtotal")));
        coupon.setMaxRedemptions(parseInteger(record.get("maxRedemptions")));
        coupon.setMaxRedemptionsPerUser(parseInteger(record.get("maxRedemptionsPerUser")));
        coupon.setMaxDiscountAmount(parseBigDecimal(record.get("maxDiscountAmount")));
        String description = record.get("description");
        coupon.setDescription(description == null || description.isBlank() ? null : description.trim());
        return coupon;
    }

    @Override
    protected void persist(Coupon entity) {
        couponRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return normalizeCode(record);
    }

    private static String normalizeCode(CSVRecord record) {
        return record.get("code").trim().toUpperCase();
    }

    /** Defaults to {@code true} on a blank cell — matches {@code CreateCouponRequest.active}'s own
     * default, and every row in this CSV that means "inactive" says so explicitly (`false`) rather
     * than relying on an absent value. */
    private static boolean parseActive(CSVRecord record) {
        String raw = record.get("active");
        return raw == null || raw.isBlank() || Boolean.parseBoolean(raw.trim());
    }

    private static Instant parseInstant(String raw) {
        return raw == null || raw.isBlank() ? null : Instant.parse(raw.trim());
    }

    private static BigDecimal parseBigDecimal(String raw) {
        return raw == null || raw.isBlank() ? null : new BigDecimal(raw.trim());
    }

    private static Integer parseInteger(String raw) {
        return raw == null || raw.isBlank() ? null : Integer.parseInt(raw.trim());
    }
}
