package com.ShopMeta.product_service.Entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A key/value specification of a product, e.g. {@code RAM = 8GB}.
 */
@Entity
@Table(name = "product_attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "attr_key", nullable = false, length = 80)
    private String attrKey;

    @Column(name = "attr_value", nullable = false, length = 255)
    private String attrValue;
}
