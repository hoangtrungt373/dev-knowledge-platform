package com.ttg.devknowledgeplatform.ecommerce.exception;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

import org.springframework.http.HttpStatus;

/**
 * Error codes owned by {@code ecommerce-service} — product categories, products, and variants
 * today; orders/payments/reviews codes will be added here as later epics are built.
 *
 * <p>Format: MODULE_ACTION_ERROR, mirroring {@code content-service}'s {@code ContentErrorCode}.
 */
@Getter
public enum EcommerceErrorCode implements ErrorCode {

    // Product Category Errors (PRODUCT_CATEGORY_*)
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT_CATEGORY_001", "Product category not found: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_NAME_CONFLICT("PRODUCT_CATEGORY_002", "A product category with name ''{0}'' already exists", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_SLUG_CONFLICT("PRODUCT_CATEGORY_003", "Unable to generate a unique slug for product category ''{0}''", HttpStatus.CONFLICT),

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

    // Product Image Errors (PRODUCT_IMAGE_*)
    PRODUCT_IMAGE_DUPLICATE_SORT_ORDER("PRODUCT_IMAGE_001",
            "Sort order {0} appears more than once in the same request", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    EcommerceErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
