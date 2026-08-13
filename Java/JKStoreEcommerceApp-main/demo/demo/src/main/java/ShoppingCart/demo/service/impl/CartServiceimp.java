package ShoppingCart.demo.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ShoppingCart.demo.Repository.CartRepository;
import ShoppingCart.demo.model.Cart;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;
import ShoppingCart.demo.service.CartService;

@Service
public class CartServiceimp implements CartService {

    @Autowired
    CartRepository cartRepo;
    

    @Override
    public void addToCart(User user, Product product){

        Cart existItems=cartRepo.findByUserAndProduct(user, product);

        if(existItems==null){

            Cart cartItems=new Cart();
            cartItems.setProduct(product);
            cartItems.setQuantity(1);
            cartItems.setPrice(product.getPrice().subtract(product.getDiscount()).doubleValue());
            cartRepo.save(cartItems);
        }
        else{
            existItems.setProduct(product);
            existItems.setQuantity(existItems.getQuantity()+1);
            existItems.setPrice(existItems.getQuantity()*(product.getPrice().subtract(product.getDiscount()).doubleValue()));
            cartRepo.save(existItems);
        }
    }

    @Override
     public List<Cart> getUserCart(User user) {
        return cartRepo.findByUser(user);
    }
}

