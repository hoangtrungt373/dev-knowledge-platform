package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.SavedAddressApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateSavedAddressRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.SavedAddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateSavedAddressRequest;
import com.ttg.devknowledgeplatform.ecommerce.mapper.SavedAddressMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link SavedAddressApi}.
 */
@RestController
@RequiredArgsConstructor
public class SavedAddressController implements SavedAddressApi {

    private final SavedAddressService savedAddressService;
    private final SavedAddressMapper savedAddressMapper;

    @Override
    public ResponseEntity<List<SavedAddressResponse>> list(String userUuid) {
        List<SavedAddressResponse> responses = savedAddressService.list(userUuid).stream()
                .map(savedAddressMapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<SavedAddressResponse> create(String userUuid, CreateSavedAddressRequest request) {
        var command = new SavedAddressCommands.Create(
                request.getLabel(), request.getFullName(), request.getPhone(), request.getEmail(), request.getLine1(),
                request.getLine2(), request.getCity(), request.getState(), request.getPostalCode(),
                request.getCountry(), request.isMakeDefault());
        var created = savedAddressService.create(userUuid, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAddressMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<SavedAddressResponse> update(String userUuid, Integer id, UpdateSavedAddressRequest request) {
        var command = new SavedAddressCommands.Update(
                request.getLabel(), request.getFullName(), request.getPhone(), request.getEmail(), request.getLine1(),
                request.getLine2(), request.getCity(), request.getState(), request.getPostalCode(),
                request.getCountry());
        var updated = savedAddressService.update(id, userUuid, command);
        return ResponseEntity.ok(savedAddressMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(String userUuid, Integer id) {
        savedAddressService.delete(id, userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SavedAddressResponse> setDefault(String userUuid, Integer id) {
        var updated = savedAddressService.setDefault(id, userUuid);
        return ResponseEntity.ok(savedAddressMapper.toResponse(updated));
    }
}
