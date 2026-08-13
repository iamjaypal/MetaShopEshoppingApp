package ShoppingCart.demo.service;

import java.util.List;



import ShoppingCart.demo.model.Cart;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;


public interface CartService {
    
    public void addToCart(User user, Product product);

    public List<Cart> getUserCart(User user);

}
