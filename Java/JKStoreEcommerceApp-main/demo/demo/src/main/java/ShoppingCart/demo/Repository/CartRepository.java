package ShoppingCart.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ShoppingCart.demo.model.Cart;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;

public interface CartRepository extends JpaRepository<Cart,Integer> {

    public Cart findByUserAndProduct(User user,Product product);

    public List<Cart> findByUser(User user);
    
    public Cart findById(int cartId);
}
