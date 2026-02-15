package com.flashsale.flashsale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseRequest {

    @NotNull(message = "Flash sale product ID is required")
    @Positive
    private Long flashSaleProductId;
}
