package ShoppingCart.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ShoppingCart.demo.model.Product;

public interface ProductRepository extends JpaRepository<Product,Integer> {

    public Boolean existsByNameAndBrand(String name, String brand);

    List<Product> findByCategoryName(String categoryName);

    List<Product> findByNameContainsIgnoreCase(String name);
}