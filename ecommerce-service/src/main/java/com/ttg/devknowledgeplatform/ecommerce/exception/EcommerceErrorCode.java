package com.ttg.devknowledgeplatform.ecommerce.exception;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

import org.springframework.http.HttpStatus;

/**
 * Error codes owned by {@code ecommerce-service} — product categories/products/variants (Epic 1)
 * and cart/checkout (Epic 2) today; payments/reviews codes will be added here as later epics are
 * built.
 *
 * <p>Format: MODULE_ACTION_ERROR, mirroring {@code content-service}'s {@code ContentErrorCode}.
 */
@Getter
public enum EcommerceErrorCode implements ErrorCode {

    // Product Category Errors (PRODUCT_CATEGORY_*)
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT_CATEGORY_001", "Product category not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_NAME_CONFLICT("PRODUCT_CATEGORY_002", "A product category with name ''{0}'' already exists", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_SLUG_CONFLICT("PRODUCT_CATEGORY_003", "Unable to generate a unique slug for product category ''{0}''", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_CYCLIC_PARENT("PRODUCT_CATEGORY_004",
            "Invalid parent: would create a cycle in the category tree", HttpStatus.BAD_REQUEST),

    // Product Errors (PRODUCT_*)
    PRODUCT_NOT_FOUND("PRODUCT_001", "Product not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_SLUG_CONFLICT("PRODUCT_002", "Unable to generate a unique slug for product ''{0}''", HttpStatus.CONFLICT),
    PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT("PRODUCT_003", "A product must have at least one variant", HttpStatus.BAD_REQUEST),
    PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT("PRODUCT_004",
            "All variants of a product must share the same attribute keys", HttpStatus.BAD_REQUEST),

    // Product Variant Errors (PRODUCT_VARIANT_*)
    PRODUCT_VARIANT_SKU_CONFLICT("PRODUCT_VARIANT_001", "A variant with SKU ''{0}'' already exists", HttpStatus.CONFLICT),
    PRODUCT_VARIANT_DUPLICATE_SKU_IN_REQUEST("PRODUCT_VARIANT_002",
            "SKU ''{0}'' appears more than once in the same request", HttpStatus.BAD_REQUEST),
    PRODUCT_VARIANT_NOT_FOUND("PRODUCT_VARIANT_003", "Variant not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_BELONGS_TO_ANOTHER_PRODUCT("PRODUCT_VARIANT_004",
            "Variant {0} does not belong to product {1}", HttpStatus.BAD_REQUEST),

    // Product Tag Errors (PRODUCT_TAG_*)
    PRODUCT_TAG_NOT_FOUND("PRODUCT_TAG_001", "Product tag not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_TAG_NAME_CONFLICT("PRODUCT_TAG_002", "A product tag with name ''{0}'' already exists", HttpStatus.CONFLICT),
    PRODUCT_TAG_SLUG_CONFLICT("PRODUCT_TAG_003", "Unable to generate a unique slug for product tag ''{0}''", HttpStatus.CONFLICT),
    PRODUCT_TAG_IN_USE("PRODUCT_TAG_004", "Product tag {0} is assigned to one or more products and cannot be deleted", HttpStatus.CONFLICT),

    // Product Image Errors (PRODUCT_IMAGE_*)
    PRODUCT_IMAGE_DUPLICATE_SORT_ORDER("PRODUCT_IMAGE_001",
            "Sort order {0} appears more than once in the same request", HttpStatus.BAD_REQUEST),
    PRODUCT_IMAGE_SORT_ORDER_CONFLICT("PRODUCT_IMAGE_002",
            "Sort order {0} is already used by another image on this product", HttpStatus.CONFLICT),
    PRODUCT_IMAGE_NOT_FOUND("PRODUCT_IMAGE_003", "Image not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_IMAGE_BELONGS_TO_ANOTHER_PRODUCT("PRODUCT_IMAGE_004",
            "Image {0} does not belong to product {1}", HttpStatus.BAD_REQUEST),

    // Cart Errors (CART_*) — Epic 2
    CART_VARIANT_UNAVAILABLE("CART_001",
            "Variant {0} is no longer available (its product has been deactivated)", HttpStatus.CONFLICT),

    // Checkout Errors (CHECKOUT_*) — Epic 2
    CHECKOUT_CART_EMPTY("CHECKOUT_001", "Your cart is empty", HttpStatus.BAD_REQUEST),
    CHECKOUT_NO_VALID_ITEMS("CHECKOUT_002",
            "None of the items in your cart are currently available", HttpStatus.CONFLICT),
    CHECKOUT_ADDRESS_REQUIRED("CHECKOUT_003",
            "A shipping address is required — provide a saved address id or the full address details",
            HttpStatus.BAD_REQUEST),

    // Saved Address Errors (SAVED_ADDRESS_*) — AddressBook
    SAVED_ADDRESS_NOT_FOUND("SAVED_ADDRESS_001", "Address not found: {0}", HttpStatus.NOT_FOUND),

    // Order Errors (ORDER_*) — Epic 3
    ORDER_INSUFFICIENT_STOCK("ORDER_001",
            "Not enough stock available for ''{0}'' — someone else may have just bought the last of it",
            HttpStatus.CONFLICT),
    ORDER_NOT_FOUND("ORDER_002", "Order not found: {0}", HttpStatus.NOT_FOUND),
    ORDER_INVALID_STATUS_TRANSITION("ORDER_003", "Cannot {0} an order in status {1}", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    EcommerceErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
