package com.shopMeta.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopMeta.auth_service.dto.AddressRequest;
import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.entity.Address;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.AddressRepository;
import com.shopMeta.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressService addressService;

    private User user() {
        return User.builder().id(1L).email("test@example.com").build();
    }

    private AddressRequest request(Boolean isDefault) {
        AddressRequest request = new AddressRequest();
        request.setFullName("Test User");
        request.setPhone("+919000000001");
        request.setLine1("1 Main St");
        request.setCity("Pune");
        request.setState("MH");
        request.setPostalCode("411001");
        request.setIsDefault(isDefault);
        return request;
    }

    private Address address(Long id, boolean isDefault) {
        return Address.builder()
                .id(id)
                .user(user())
                .fullName("Test User")
                .phone("+919000000001")
                .line1("1 Main St")
                .city("Pune")
                .state("MH")
                .postalCode("411001")
                .country("IN")
                .isDefault(isDefault)
                .build();
    }

    @Test
    void create_firstAddressBecomesDefault() {

        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(addressRepository.countByUserId(1L)).willReturn(0L);
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> {
            Address saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AddressResponse response = addressService.create(1L, request(null));

        assertThat(response.getIsDefault()).isTrue();
        assertThat(response.getCountry()).isEqualTo("IN");
    }

    @Test
    void create_secondAddressIsNotDefaultByDefault() {

        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(addressRepository.countByUserId(1L)).willReturn(1L);
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> {
            Address saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AddressResponse response = addressService.create(1L, request(null));

        assertThat(response.getIsDefault()).isFalse();
        verify(addressRepository, never()).saveAll(any());
    }

    @Test
    void create_withIsDefaultTrue_clearsThePreviousDefault() {

        Address existingDefault = address(1L, true);

        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(addressRepository.countByUserId(1L)).willReturn(1L);
        given(addressRepository.findByUserId(1L)).willReturn(new ArrayList<>(List.of(existingDefault)));
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> {
            Address saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return saved;
        });

        AddressResponse response = addressService.create(1L, request(true));

        assertThat(response.getIsDefault()).isTrue();
        assertThat(existingDefault.getIsDefault()).isFalse();
    }

    @Test
    void create_beyondLimit_isRejected() {

        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(addressRepository.countByUserId(1L)).willReturn(10L);

        assertThatThrownBy(() -> addressService.create(1L, request(null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 10 addresses");

        verify(addressRepository, never()).save(any());
    }

    @Test
    void setDefault_clearsThePreviousDefault() {

        Address current = address(1L, true);
        Address target = address(2L, false);

        given(addressRepository.findByIdAndUserId(2L, 1L)).willReturn(Optional.of(target));
        given(addressRepository.findByUserId(1L)).willReturn(new ArrayList<>(List.of(current, target)));
        given(addressRepository.save(target)).willReturn(target);

        AddressResponse response = addressService.setDefault(1L, 2L);

        assertThat(response.getIsDefault()).isTrue();
        assertThat(current.getIsDefault()).isFalse();
    }

    @Test
    void delete_defaultAddress_promotesTheOldestRemaining() {

        Address defaultAddress = address(1L, true);
        Address next = address(2L, false);

        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(defaultAddress));
        given(addressRepository.findFirstByUserIdOrderByIdAsc(1L)).willReturn(Optional.of(next));
        given(addressRepository.save(next)).willReturn(next);

        addressService.delete(1L, 1L);

        verify(addressRepository).delete(defaultAddress);
        assertThat(next.getIsDefault()).isTrue();
    }

    @Test
    void delete_nonDefaultAddress_doesNotPromoteAnything() {

        Address other = address(2L, false);

        given(addressRepository.findByIdAndUserId(2L, 1L)).willReturn(Optional.of(other));

        addressService.delete(1L, 2L);

        verify(addressRepository).delete(other);
        verify(addressRepository, never()).findFirstByUserIdOrderByIdAsc(any());
    }

    @Test
    void get_addressOfAnotherUser_isNotFound() {

        given(addressRepository.findByIdAndUserId(5L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.get(1L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    void list_returnsAddressesForTheUser() {

        given(addressRepository.findByUserIdOrderByIsDefaultDescIdAsc(1L))
                .willReturn(List.of(address(1L, true), address(2L, false)));

        List<AddressResponse> addresses = addressService.list(1L);

        assertThat(addresses).hasSize(2);
        assertThat(addresses.get(0).getIsDefault()).isTrue();
    }
}
