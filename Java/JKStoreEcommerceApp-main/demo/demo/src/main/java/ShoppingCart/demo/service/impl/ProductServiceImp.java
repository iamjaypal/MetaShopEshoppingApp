package ShoppingCart.demo.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ShoppingCart.demo.Repository.ProductRepository;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.service.ProductService;

@Service
public class ProductServiceImp implements ProductService {

    @Autowired
    ProductRepository prodRepo;

    @Override
    public Product saveProduct(Product product) {
        return prodRepo.save(product);

    }

    @Override
    public List<Product> getAllProducts() {
        return prodRepo.findAll();
    }

    @Override
    public Product getProductById(int id) {
        return prodRepo.getReferenceById(id);
    }

    @Override
    public Boolean existsByNameAndBrand(String name, String brand) {
        return prodRepo.existsByNameAndBrand(name, brand);
    }

    @Override
    public List<Product> getProductsByCategory(String categoryName) {
        return prodRepo.findByCategoryName(categoryName);
    }

    @Override
    public List<Product> findByNameContainsIgnoreCase(String name) {
        return prodRepo.findByNameContainsIgnoreCase(name);
    }
}
