package com.ShopMeta.product_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ShopMeta.product_service.Entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

      boolean existsBySku(String sku);

}
