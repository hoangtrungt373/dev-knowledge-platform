package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutCommands;

import org.mapstruct.Mapper;

/**
 * Builds the {@link Address} snapshot {@code CheckoutServiceImpl#resolveAddress} copies onto an
 * {@link com.ttg.devknowledgeplatform.ecommerce.entity.Order} — from either a fresh, one-off
 * {@link CheckoutCommands.AddressInput} or an existing {@link SavedAddress} AddressBook entry.
 * Both source shapes carry the identical nine address fields, in the identical order, as
 * {@link Address} itself, so MapStruct needs no {@code @Mapping} overrides for either method — a
 * code-quality-audit finding: {@code CheckoutServiceImpl} used to hand-copy both shapes via two
 * private static {@code toAddress} methods, which this module's own convention reserves for
 * MapStruct (see root {@code CLAUDE.md}'s "DTOs ↔ entities" rule).
 */
@Mapper(componentModel = "spring")
public interface AddressMapper {

    Address toAddress(CheckoutCommands.AddressInput input);

    Address toAddress(SavedAddress saved);
}
