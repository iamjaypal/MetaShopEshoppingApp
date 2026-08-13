package ShoppingCart.demo.service;

import java.util.List;

import ShoppingCart.demo.model.Product;

public interface ProductService {

    public Product saveProduct(Product product);

    public List<Product> getAllProducts();

    public Product getProductById(int id);

    public Boolean existsByNameAndBrand(String name, String brand);

    public List<Product> getProductsByCategory(String categoryName);

    public List<Product> findByNameContainsIgnoreCase(String name);
    
}
