package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.dto.AddressRequest;
import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.entity.Address;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.AddressRepository;
import com.shopMeta.auth_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Address book (spec 7.3/7.4).
 *
 * <ul>
 *   <li>At most {@value #MAX_ADDRESSES_PER_USER} addresses per user.</li>
 *   <li>Exactly one default address; the first address becomes default automatically.</li>
 *   <li>Every operation is scoped to the owner; another user's address is reported as 404.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    public static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {

        User user = requireUser(userId);

        long existing = addressRepository.countByUserId(userId);

        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new BusinessRuleException(
                    "A user can have at most " + MAX_ADDRESSES_PER_USER + " addresses");
        }

        // The first address is always the default.
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            clearDefault(userId);
        }

        Address address = Address.builder()
                .user(user)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .line1(request.getLine1().trim())
                .line2(request.getLine2())
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .postalCode(request.getPostalCode().trim())
                .country(request.getCountry() == null ? "IN" : request.getCountry().toUpperCase())
                .isDefault(makeDefault)
                .build();

        return toResponse(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(Long userId, Long addressId) {
        return toResponse(requireOwned(userId, addressId));
    }

    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {

        Address address = requireOwned(userId, addressId);

        address.setFullName(request.getFullName().trim());
        address.setPhone(request.getPhone());
        address.setLine1(request.getLine1().trim());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountry(request.getCountry() == null
                ? address.getCountry()
                : request.getCountry().toUpperCase());

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(true);
        }

        return toResponse(addressRepository.save(address));
    }

    /**
     * Deletes an address. If it was the default one, the oldest remaining address is promoted
     * so the user always keeps exactly one default (while they have any address at all).
     */
    @Transactional
    public void delete(Long userId, Long addressId) {

        Address address = requireOwned(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());

        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            addressRepository.findFirstByUserIdOrderByIdAsc(userId).ifPresent(next -> {
                next.setIsDefault(true);
                addressRepository.save(next);
            });
        }
    }

    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {

        Address address = requireOwned(userId, addressId);

        clearDefault(userId);
        address.setIsDefault(true);

        return toResponse(addressRepository.save(address));
    }

    private void clearDefault(Long userId) {

        List<Address> addresses = addressRepository.findByUserId(userId);

        boolean changed = false;
        for (Address address : addresses) {
            if (Boolean.TRUE.equals(address.getIsDefault())) {
                address.setIsDefault(false);
                changed = true;
            }
        }

        if (changed) {
            addressRepository.saveAll(addresses);
        }
    }

    private Address requireOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AddressResponse toResponse(Address address) {

        return AddressResponse.builder()
                .id(address.getId())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
