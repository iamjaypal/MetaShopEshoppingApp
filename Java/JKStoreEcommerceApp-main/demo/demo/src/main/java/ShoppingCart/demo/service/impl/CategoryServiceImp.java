package ShoppingCart.demo.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ShoppingCart.demo.Repository.CategoryRepository;
import ShoppingCart.demo.model.Category;

import ShoppingCart.demo.service.CategoryService;

@Service
public class CategoryServiceImp  implements CategoryService{
    
    @Autowired
    private CategoryRepository categoryRepo;

    @Override
    public Category saveCategory(Category category){
        return categoryRepo.save(category);
    }
    @Override
    public List<Category> getAllCategory(){
        return categoryRepo.findAll();
    }
    @Override
    public Category getCategoryById(int id){
        return categoryRepo.getReferenceById(id);
    }
    @Override
    public Boolean existCategory(String name){
         return categoryRepo.existsByName(name);
    }

}
