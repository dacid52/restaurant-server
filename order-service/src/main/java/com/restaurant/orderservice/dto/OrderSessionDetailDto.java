package com.restaurant.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrderSessionDetailDto {
    private OrderSessionSummaryDto summary;
    private List<OrderResponseDto> orders;
}
