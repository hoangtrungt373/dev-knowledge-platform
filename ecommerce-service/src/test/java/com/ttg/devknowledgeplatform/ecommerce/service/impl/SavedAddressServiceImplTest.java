package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.SavedAddressRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SavedAddressServiceImpl} — the AddressBook feature.
 */
@ExtendWith(MockitoExtension.class)
class SavedAddressServiceImplTest {

    private static final String OWNER_UUID = "user-uuid-1";

    @Mock
    private SavedAddressRepository savedAddressRepository;

    @InjectMocks
    private SavedAddressServiceImpl service;

    private SavedAddress existing;

    @BeforeEach
    void setUp() {
        existing = new SavedAddress();
        existing.setId(1);
        existing.setOwnerUuid(OWNER_UUID);
        existing.setLabel("Home");
        existing.setFullName("Ada Lovelace");
        existing.setLine1("1 Analytical Engine Way");
        existing.setCity("London");
        existing.setState("England");
        existing.setPostalCode("SW1A 1AA");
        existing.setCountry("UK");
        existing.setDefaultAddress(false);
    }

    private static SavedAddressCommands.Create createCommand(boolean makeDefault) {
        return new SavedAddressCommands.Create(
                "Work", "Grace Hopper", "1 Compiler Ave", null, "Arlington", "VA", "22201", "USA", makeDefault);
    }

    @Nested
    class ListAddresses {

        @Test
        void returnsEveryAddressForTheOwnerDefaultFirst() {
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of(existing));

            assertThat(service.list(OWNER_UUID)).containsExactly(existing);
        }
    }

    @Nested
    class Create {

        @Test
        void theFirstAddressIsAutoDefaultedRegardlessOfTheFlag() {
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of());
            when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SavedAddress result = service.create(OWNER_UUID, createCommand(false));

            assertThat(result.isDefaultAddress()).isTrue();
            verify(savedAddressRepository).clearDefaultForOwner(OWNER_UUID);
        }

        @Test
        void aSubsequentAddressIsNotDefaultUnlessExplicitlyRequested() {
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of(existing));
            when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SavedAddress result = service.create(OWNER_UUID, createCommand(false));

            assertThat(result.isDefaultAddress()).isFalse();
            verify(savedAddressRepository, never()).clearDefaultForOwner(any());
        }

        @Test
        void explicitlyRequestingDefaultClearsThePreviousOne() {
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of(existing));
            when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SavedAddress result = service.create(OWNER_UUID, createCommand(true));

            assertThat(result.isDefaultAddress()).isTrue();
            verify(savedAddressRepository).clearDefaultForOwner(OWNER_UUID);
        }
    }

    @Nested
    class Update {

        @Test
        void updatesFieldsOnAnOwnedAddress() {
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));
            when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
            var command = new SavedAddressCommands.Update(
                    "Office", "Ada L.", "221B Baker St", "Flat 2", "London", "England", "NW1 6XE", "UK");

            SavedAddress result = service.update(1, OWNER_UUID, command);

            assertThat(result.getLabel()).isEqualTo("Office");
            assertThat(result.getLine1()).isEqualTo("221B Baker St");
            assertThat(result.getPostalCode()).isEqualTo("NW1 6XE");
        }

        @Test
        void throwsWhenNoAddressWithThisIdBelongsToTheCaller() {
            when(savedAddressRepository.findByIdAndOwnerUuid(99, OWNER_UUID)).thenReturn(Optional.empty());
            var command = new SavedAddressCommands.Update("L", "N", "L1", null, "C", "S", "P", "Co");

            assertThatThrownBy(() -> service.update(99, OWNER_UUID, command))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.SAVED_ADDRESS_NOT_FOUND);
        }
    }

    @Nested
    class Delete {

        @Test
        void autoPromotesTheMostRecentRemainingAddressWhenTheDefaultOneIsDeleted() {
            existing.setDefaultAddress(true);
            SavedAddress remaining = new SavedAddress();
            remaining.setId(2);
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of(remaining));

            service.delete(1, OWNER_UUID);

            verify(savedAddressRepository).delete(existing);
            assertThat(remaining.isDefaultAddress()).isTrue();
            verify(savedAddressRepository).save(remaining);
        }

        @Test
        void doesNotPromoteAnythingWhenTheDeletedAddressWasNotDefault() {
            existing.setDefaultAddress(false);
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));

            service.delete(1, OWNER_UUID);

            verify(savedAddressRepository).delete(existing);
            verify(savedAddressRepository, never()).findByOwnerUuidOrderByDefaultAddressDescIdDesc(any());
            verify(savedAddressRepository, never()).save(any());
        }

        @Test
        void isANoOpWhenDeletingTheLastRemainingAddress() {
            existing.setDefaultAddress(true);
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));
            when(savedAddressRepository.findByOwnerUuidOrderByDefaultAddressDescIdDesc(OWNER_UUID))
                    .thenReturn(List.of());

            service.delete(1, OWNER_UUID);

            verify(savedAddressRepository).delete(existing);
            verify(savedAddressRepository, never()).save(any());
        }

        @Test
        void throwsWhenNoAddressWithThisIdBelongsToTheCaller() {
            when(savedAddressRepository.findByIdAndOwnerUuid(99, OWNER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99, OWNER_UUID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class SetDefault {

        @Test
        void promotesTheGivenAddressAndDemotesWhicheverHeldDefaultBefore() {
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));
            when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SavedAddress result = service.setDefault(1, OWNER_UUID);

            assertThat(result.isDefaultAddress()).isTrue();
            verify(savedAddressRepository).clearDefaultForOwner(OWNER_UUID);
            verify(savedAddressRepository).save(existing);
        }

        @Test
        void isANoOpWhenTheAddressIsAlreadyTheDefault() {
            existing.setDefaultAddress(true);
            when(savedAddressRepository.findByIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(Optional.of(existing));

            SavedAddress result = service.setDefault(1, OWNER_UUID);

            assertThat(result.isDefaultAddress()).isTrue();
            verify(savedAddressRepository, never()).clearDefaultForOwner(any());
            verify(savedAddressRepository, never()).save(any());
        }

        @Test
        void throwsWhenNoAddressWithThisIdBelongsToTheCaller() {
            when(savedAddressRepository.findByIdAndOwnerUuid(99, OWNER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setDefault(99, OWNER_UUID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
