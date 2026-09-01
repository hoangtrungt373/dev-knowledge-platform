package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.SavedAddressRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link SavedAddressService} — the AddressBook feature.
 *
 * <p>"At most one default address per owner" is enforced twice: here (unset-then-set, inside the
 * same transaction as the new default's own save via {@link SavedAddressRepository#clearDefaultForOwner})
 * and by a partial unique index in the database ({@code UX_SAVED_ADDRESS_OWNER_DEFAULT}, see this
 * table's own migration) — the DB constraint is the real guarantee (defends against any future
 * code path that forgets this dance), this class' own logic is what makes the common path
 * actually succeed instead of just failing loudly against the index.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class SavedAddressServiceImpl implements SavedAddressService {

    private final SavedAddressRepository savedAddressRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SavedAddress> list(String ownerUuid) {
        return savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(ownerUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedAddress getOwned(Integer id, String ownerUuid) {
        return findOwned(id, ownerUuid);
    }

    @Override
    public SavedAddress create(String ownerUuid, SavedAddressCommands.Create command) {
        SavedAddress address = new SavedAddress();
        address.setOwnerUuid(ownerUuid);
        applyFields(address, command.label(), command.fullName(), command.phone(), command.email(), command.line1(),
                command.line2(), command.city(), command.state(), command.postalCode(), command.country());

        // The very first address for a caller is always the default, regardless of whether it was
        // explicitly requested — there's no sensible "no default while at least one address
        // exists" state, and forcing a separate set-default call just for the first address would
        // be a pointless extra step for the overwhelmingly common case.
        boolean isFirstAddress = savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(ownerUuid).isEmpty();
        boolean shouldBeDefault = isFirstAddress || command.makeDefault();
        if (shouldBeDefault) {
            savedAddressRepository.clearDefaultForOwner(ownerUuid);
        }
        address.setDefaultAddress(shouldBeDefault);

        SavedAddress saved = savedAddressRepository.save(address);
        log.info("Created saved address id={} for ownerUuid={} default={}",
                saved.getId(), ownerUuid, saved.isDefaultAddress());
        return saved;
    }

    @Override
    public SavedAddress update(Integer id, String ownerUuid, SavedAddressCommands.Update command) {
        SavedAddress address = findOwned(id, ownerUuid);
        applyFields(address, command.label(), command.fullName(), command.phone(), command.email(), command.line1(),
                command.line2(), command.city(), command.state(), command.postalCode(), command.country());
        SavedAddress saved = savedAddressRepository.save(address);
        log.info("Updated saved address id={} for ownerUuid={}", id, ownerUuid);
        return saved;
    }

    @Override
    public void delete(Integer id, String ownerUuid) {
        SavedAddress address = findOwned(id, ownerUuid);
        boolean wasDefault = address.isDefaultAddress();
        savedAddressRepository.delete(address);

        if (wasDefault) {
            // Never leave "addresses exist but none is the default" behind — auto-promote the
            // caller's own most-recently-created remaining address, same reasoning as the
            // first-address auto-default in create(). A no-op when this was the last address.
            savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(ownerUuid).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultAddress(true);
                        savedAddressRepository.save(next);
                    });
        }
        log.info("Deleted saved address id={} for ownerUuid={} (was default={})", id, ownerUuid, wasDefault);
    }

    @Override
    public SavedAddress setDefault(Integer id, String ownerUuid) {
        SavedAddress address = findOwned(id, ownerUuid);
        if (!address.isDefaultAddress()) {
            savedAddressRepository.clearDefaultForOwner(ownerUuid);
            address.setDefaultAddress(true);
            address = savedAddressRepository.save(address);
        }
        log.info("Set saved address id={} as default for ownerUuid={}", id, ownerUuid);
        return address;
    }

    private SavedAddress findOwned(Integer id, String ownerUuid) {
        return Validator.notFound(
                savedAddressRepository.findByIdAndOwnerUuid(id, ownerUuid),
                EcommerceErrorCode.SAVED_ADDRESS_NOT_FOUND, id);
    }

    private static void applyFields(
            SavedAddress address, String label, String fullName, String phone, String email, String line1,
            String line2, String city, String state, String postalCode, String country) {
        address.setLabel(label);
        address.setFullName(fullName);
        address.setPhone(phone);
        address.setEmail(email);
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postalCode);
        address.setCountry(country);
    }
}
