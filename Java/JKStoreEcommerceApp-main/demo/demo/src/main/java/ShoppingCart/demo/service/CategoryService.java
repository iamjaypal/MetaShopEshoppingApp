package ShoppingCart.demo.service;

import java.util.List;

import ShoppingCart.demo.model.Category;

public interface CategoryService {

    public Category saveCategory(Category category);

    public List<Category> getAllCategory();

    public Category getCategoryById(int id);
    
    public Boolean existCategory(String name);
    
}
