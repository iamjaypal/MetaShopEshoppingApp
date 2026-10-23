package com.ShopMeta.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;

    private String name;

    private String slug;

    private String description;

    private Long parentId;

    private String parentName;

    private Boolean active;

    /** Populated only by the tree endpoint; empty for flat responses. */
    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
