package com.shopMeta.inventory_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.inventory_service.dto.AvailabilityBatchResponse;
import com.shopMeta.inventory_service.dto.AvailabilityItemResponse;
import com.shopMeta.inventory_service.dto.ReservationItemResponse;
import com.shopMeta.inventory_service.dto.ReservationResponse;
import com.shopMeta.inventory_service.dto.ReserveRequest;
import com.shopMeta.inventory_service.dto.UnavailableItem;
import com.shopMeta.inventory_service.exception.BusinessRuleException;
import com.shopMeta.inventory_service.exception.InsufficientStockException;
import com.shopMeta.inventory_service.exception.ResourceNotFoundException;
import com.shopMeta.inventory_service.service.ReservationService;

@WebMvcTest(InternalInventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void reserve_returnsReservationAsPlainJson() throws Exception {

        given(reservationService.reserve(any(ReserveRequest.class)))
                .willReturn(ReservationResponse.builder()
                        .orderId(500L)
                        .status("RESERVED")
                        .items(List.of(ReservationItemResponse.builder()
                                .productId(1L).quantity(2).status("RESERVED").build()))
                        .build());

        mockMvc.perform(post("/api/v1/internal/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":500,"items":[{"productId":1,"quantity":2}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void reserve_outOfStock_returnsConflictListingTheUnavailableItems() throws Exception {

        willThrow(new InsufficientStockException("Insufficient stock for 1 item(s)",
                List.of(UnavailableItem.builder()
                        .productId(2L).requested(5).available(1).build())))
                .given(reservationService).reserve(any(ReserveRequest.class));

        mockMvc.perform(post("/api/v1/internal/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":500,"items":[{"productId":2,"quantity":5}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].productId").value(2))
                .andExpect(jsonPath("$.data[0].requested").value(5))
                .andExpect(jsonPath("$.data[0].available").value(1));
    }

    @Test
    void reserve_emptyItems_isBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/internal/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":500,"items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void release_returnsReleasedStatus() throws Exception {

        given(reservationService.release(500L))
                .willReturn(ReservationResponse.builder().orderId(500L).status("RELEASED").items(List.of()).build());

        mockMvc.perform(post("/api/v1/internal/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void release_unknownOrder_returnsNotFound() throws Exception {

        willThrow(new ResourceNotFoundException("No reservations found for order 999"))
                .given(reservationService).release(999L);

        mockMvc.perform(post("/api/v1/internal/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":999}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_alreadyReleased_returnsUnprocessable() throws Exception {

        willThrow(new BusinessRuleException(
                "Reservation for product 1 was released and can no longer be confirmed"))
                .given(reservationService).confirm(500L);

        mockMvc.perform(post("/api/v1/internal/inventory/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":500}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void availability_returnsExactQuantities() throws Exception {

        given(reservationService.checkAvailability(any()))
                .willReturn(AvailabilityBatchResponse.builder()
                        .items(List.of(AvailabilityItemResponse.builder()
                                .productId(1L).sku("SKU-1").available(10)
                                .requested(3).sufficient(true).inStock(true)
                                .build()))
                        .allAvailable(true)
                        .build());

        mockMvc.perform(post("/api/v1/internal/inventory/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allAvailable").value(true))
                .andExpect(jsonPath("$.items[0].available").value(10))
                .andExpect(jsonPath("$.items[0].sufficient").value(true));
    }
}
