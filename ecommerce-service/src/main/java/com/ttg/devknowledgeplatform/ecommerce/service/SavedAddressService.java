package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;

import java.util.List;

/**
 * A shopper's own reusable address book — every method is scoped to a single caller's
 * {@code ownerUuid} (the JWT's own {@code sub} claim), never another shopper's addresses; an id
 * that exists but belongs to someone else is indistinguishable from one that doesn't exist at all
 * (see {@code SavedAddressServiceImpl#findOwned}), the same convention {@code OrderService}
 * already established for this module.
 */
public interface SavedAddressService {

    /** Default-first, then most-recently-created. */
    List<SavedAddress> list(String ownerUuid);

    /**
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no
     *         address with this id belongs to {@code ownerUuid}
     */
    SavedAddress getOwned(Integer id, String ownerUuid);

    SavedAddress create(String ownerUuid, SavedAddressCommands.Create command);

    SavedAddress update(Integer id, String ownerUuid, SavedAddressCommands.Update command);

    void delete(Integer id, String ownerUuid);

    /** Promotes {@code id} to the caller's default address, demoting whichever one held it before. */
    SavedAddress setDefault(Integer id, String ownerUuid);
}
