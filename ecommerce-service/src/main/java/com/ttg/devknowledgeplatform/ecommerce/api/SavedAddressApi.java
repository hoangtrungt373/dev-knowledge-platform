package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateSavedAddressRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.SavedAddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateSavedAddressRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * HTTP contract for the shopper's own AddressBook. Authenticated-only, shopper-facing (never
 * admin-gated — this is the caller's own data, same shape as {@code CartApi}/{@code OrderApi}):
 * every method resolves the caller via {@code @CurrentUserId}, and {@code /api/v1/addresses/**}
 * falls under this module's {@code security/SecurityConfig}'s default
 * {@code anyRequest().authenticated()} rule, no new rule needed.
 *
 * <p>No admin surface exists for this resource at all — an AddressBook entry has exactly one
 * legitimate reader/writer (its own owner), unlike {@code ProductTag}/{@code ProductCategory},
 * which an admin manages on behalf of every shopper.
 */
@RequestMapping("/api/v1/addresses")
public interface SavedAddressApi {

    /**
     * Lists the caller's own saved addresses, default-first.
     *
     * @param userUuid the caller's Keycloak UUID
     * @return {@code 200} with every address this caller owns
     */
    @GetMapping
    ResponseEntity<List<SavedAddressResponse>> list(@CurrentUserId String userUuid);

    /**
     * Adds a new address to the caller's AddressBook.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param request  the address to save
     * @return {@code 201} with the created address
     */
    @PostMapping
    ResponseEntity<SavedAddressResponse> create(@CurrentUserId String userUuid, @Valid @RequestBody CreateSavedAddressRequest request);

    /**
     * Edits an existing address the caller owns.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       the address to edit
     * @param request  the new field values
     * @return {@code 200} with the updated address
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code 404} if no address
     *         with this id belongs to the caller
     */
    @PutMapping("/{id}")
    ResponseEntity<SavedAddressResponse> update(
            @CurrentUserId String userUuid, @PathVariable Integer id, @Valid @RequestBody UpdateSavedAddressRequest request);

    /**
     * Removes an address the caller owns. If it was the caller's default, the most recently
     * created remaining address (if any) is auto-promoted to default.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       the address to remove
     * @return {@code 204}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId String userUuid, @PathVariable Integer id);

    /**
     * Promotes an address the caller owns to their default, demoting whichever one held it before.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       the address to promote
     * @return {@code 200} with the updated address
     */
    @PostMapping("/{id}/set-default")
    ResponseEntity<SavedAddressResponse> setDefault(@CurrentUserId String userUuid, @PathVariable Integer id);
}
