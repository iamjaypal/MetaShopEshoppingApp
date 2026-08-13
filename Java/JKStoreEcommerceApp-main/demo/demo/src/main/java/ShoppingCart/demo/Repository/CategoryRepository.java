package ShoppingCart.demo.Repository;



import org.springframework.data.jpa.repository.JpaRepository;

import ShoppingCart.demo.model.Category;

public interface CategoryRepository extends JpaRepository<Category,Integer> {

    public Boolean existsByName(String name);
    
}
