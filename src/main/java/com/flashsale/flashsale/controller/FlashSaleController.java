package com.flashsale.flashsale.controller;

import com.flashsale.common.dto.ApiResponse;
import com.flashsale.flashsale.dto.FlashSaleItemResponse;
import com.flashsale.flashsale.dto.PurchaseRequest;
import com.flashsale.flashsale.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Flash Sale", description = "Flash sale browsing and purchase")
@RestController
@RequestMapping("/api/v1/flash-sale")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "Get all flash sale items active right now")
    @GetMapping("/items")
    public ApiResponse<List<FlashSaleItemResponse>> getActiveItems() {
        List<FlashSaleItemResponse> items = flashSaleService.getActiveFlashSales();
        return ApiResponse.success(items);
    }

    @Operation(summary = "Purchase a flash sale item (requires JWT)")
    @PostMapping("/purchase")
    public ApiResponse<Map<String, String>> purchase(@Valid @RequestBody PurchaseRequest request,
                                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String orderNo = flashSaleService.attemptPurchase(userId, request.getFlashSaleProductId());
        return ApiResponse.success("Purchase successful", Map.of("orderNo", orderNo));
    }
}
