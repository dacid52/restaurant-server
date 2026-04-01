package com.restaurant.tableservice.controller;

import com.restaurant.tableservice.entity.RestaurantLayout;
import com.restaurant.tableservice.repository.RestaurantLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/tables/layouts")
@RequiredArgsConstructor
public class LayoutController {

    private final RestaurantLayoutRepository layoutRepository;

    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<String> getLayout() {
        return layoutRepository.findById("main")
                .map(layout -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(layout.getLayoutData()))
                .orElse(ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("[]"));
    }

    @PostMapping
    public ResponseEntity<Void> updateLayout(@RequestBody @NonNull String layoutData) {
        RestaurantLayout layout = layoutRepository.findById("main")
                .orElse(new RestaurantLayout());
        layout.setId("main");
        layout.setLayoutData(layoutData);
        layoutRepository.save(layout);
        return ResponseEntity.ok().build();
    }
}
