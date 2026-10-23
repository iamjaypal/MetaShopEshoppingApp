package com.ShopMeta.product_service.config;

import com.ShopMeta.product_service.Entity.Brand;
import com.ShopMeta.product_service.Entity.Category;
import com.ShopMeta.product_service.Entity.Product;
import com.ShopMeta.product_service.Entity.ProductAttribute;
import com.ShopMeta.product_service.Entity.ProductImage;
import com.ShopMeta.product_service.repository.BrandRepository;
import com.ShopMeta.product_service.repository.CategoryRepository;
import com.ShopMeta.product_service.repository.ProductRepository;
import com.ShopMeta.product_service.util.SlugUtils;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds demo catalog data (spec section 20): 6 categories, 10 brands, 50 products with images
 * and attributes. Runs outside the {@code prod} profile only, and is idempotent (skips when the
 * catalog is already populated).
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final int PRODUCT_COUNT = 50;

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {

        if (productRepository.count() > 0) {
            log.info("Catalog already populated; skipping seed data.");
            return;
        }

        log.info("Seeding MetaShop product catalog ...");

        List<Category> categories = seedCategories();
        List<Brand> brands = seedBrands();
        seedProducts(categories, brands);

        log.info("Seed complete: {} categories, {} brands, {} products.",
                categories.size(), brands.size(), PRODUCT_COUNT);
    }

    private List<Category> seedCategories() {

        Category electronics = categoryRepository.save(Category.builder()
                .name("Electronics").slug("electronics")
                .description("Devices and gadgets").active(true).build());

        List<Category> categories = new ArrayList<>();
        categories.add(electronics);
        categories.add(categoryRepository.save(Category.builder()
                .name("Mobiles").slug("mobiles").description("Smartphones")
                .parent(electronics).active(true).build()));
        categories.add(categoryRepository.save(Category.builder()
                .name("Laptops").slug("laptops").description("Laptops and notebooks")
                .parent(electronics).active(true).build()));
        categories.add(categoryRepository.save(Category.builder()
                .name("Fashion").slug("fashion").description("Clothing and accessories")
                .active(true).build()));
        categories.add(categoryRepository.save(Category.builder()
                .name("Home & Kitchen").slug("home-kitchen").description("Home essentials")
                .active(true).build()));
        categories.add(categoryRepository.save(Category.builder()
                .name("Sports").slug("sports").description("Sports and fitness")
                .active(true).build()));

        return categories;
    }

    private List<Brand> seedBrands() {

        String[] names = { "Apple", "Samsung", "Sony", "Nike", "Adidas", "Dell", "HP", "Puma", "LG", "OnePlus" };

        List<Brand> brands = new ArrayList<>();

        for (String name : names) {
            brands.add(brandRepository.save(Brand.builder()
                    .name(name)
                    .slug(SlugUtils.toSlug(name))
                    .logoUrl("https://picsum.photos/seed/brand-" + SlugUtils.toSlug(name) + "/120/120")
                    .active(true)
                    .build()));
        }

        return brands;
    }

    private void seedProducts(List<Category> categories, List<Brand> brands) {

        for (int i = 0; i < PRODUCT_COUNT; i++) {

            Category category = categories.get(i % categories.size());
            Brand brand = brands.get(i % brands.size());

            String name = buildName(category, brand, i);
            String sku = "SKU-" + (1000 + i);

            BigDecimal price = BigDecimal.valueOf(999 + (i * 137L) % 60000L)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal mrp = price.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);

            Product product = Product.builder()
                    .sku(sku)
                    .name(name)
                    .slug(SlugUtils.toSlug(name))
                    .shortDescription(name + " - " + category.getName() + " by " + brand.getName())
                    .description("Demo product seeded for MetaShop. Category: " + category.getName()
                            + ", brand: " + brand.getName() + ".")
                    .price(price)
                    .mrp(mrp)
                    .category(category)
                    .brand(brand)
                    .active(true)
                    .averageRating(BigDecimal.valueOf(3.5 + (i % 15) / 10.0).setScale(1, RoundingMode.HALF_UP))
                    .ratingCount(10 + (i * 7) % 400)
                    .build();

            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .url("https://picsum.photos/seed/metashop-" + sku + "/600/600")
                    .altText(name)
                    .sortOrder(0)
                    .primary(true)
                    .build());

            product.getAttributes().add(ProductAttribute.builder()
                    .product(product).attrKey("Brand").attrValue(brand.getName()).build());

            product.getAttributes().add(ProductAttribute.builder()
                    .product(product).attrKey(attributeKeyFor(category)).attrValue(attributeValueFor(i)).build());

            productRepository.save(product);
        }
    }

    private String buildName(Category category, Brand brand, int index) {

        int model = index + 1;

        return switch (category.getSlug()) {
            case "mobiles" -> brand.getName() + " Smartphone " + model;
            case "laptops" -> brand.getName() + " Laptop " + model;
            case "fashion" -> brand.getName() + " T-Shirt " + model;
            case "home-kitchen" -> brand.getName() + " Blender " + model;
            case "sports" -> brand.getName() + " Running Shoes " + model;
            default -> brand.getName() + " Gadget " + model;
        };
    }

    private String attributeKeyFor(Category category) {

        return switch (category.getSlug()) {
            case "mobiles" -> "RAM";
            case "laptops" -> "Storage";
            case "fashion" -> "Size";
            case "sports" -> "Size";
            default -> "Warranty";
        };
    }

    private String attributeValueFor(int index) {

        String[] values = { "8GB", "16GB", "256GB", "512GB", "M", "L", "XL", "1 Year", "2 Years" };

        return values[index % values.length];
    }
}
