package ShoppingCart.demo.Controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ShoppingCart.demo.Repository.CartRepository;
import ShoppingCart.demo.Repository.CategoryRepository;
import ShoppingCart.demo.Repository.ProductRepository;

import ShoppingCart.demo.model.Cart;
import ShoppingCart.demo.model.Category;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {


    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private CartRepository cartRepo;

    @Autowired
    private CategoryRepository cateRepo;

    @Autowired
    private ProductRepository prodRepo;

    @GetMapping("/index")
    public String home(Model model,HttpSession session,
            @RequestParam(value = "category", required = false) String categoryName) {
                
        List<Category> categories = cateRepo.findAll();

         User user = (User) session.getAttribute("currentuser");
        if (user == null) {
            return "redirect:/login";
        }
        List<Cart> cartItems = cartRepo.findByUser(user);
        int countCart = cartItems.size();
        List<Product> products;
        if (categoryName != null) {
            products = prodRepo.findByCategoryName(categoryName);
        } else {
            products = prodRepo.findAll();
        }

        model.addAttribute("countCart", countCart);
        model.addAttribute("category", categories);
        model.addAttribute("products", products);

        return "user/index";
    }

    @GetMapping("/profile")
    public String profile(HttpSession session){
        User user = (User) session.getAttribute("currentuser");
        session.setAttribute("user", user);
         if (user == null) {
            return "redirect:/login";
        }
        return "user/profile";
    }

    @PostMapping("/addCart")
    public String addToCart(@RequestParam("pid") int productId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = (User) session.getAttribute("currentuser");

        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please login first.");
            return "redirect:/login";
        }

        Product product = productRepo.findById(productId).orElse(null);

        if (product == null || product.getQuantity() <= 0) {
            redirectAttributes.addFlashAttribute("errorMsg", "Product not available");
            return "redirect:/product/" + productId;
        }

        Cart existingCartItem = cartRepo.findByUserAndProduct(user, product);
        if (existingCartItem != null) {
            existingCartItem.setQuantity(existingCartItem.getQuantity() + 1);
            cartRepo.save(existingCartItem);
        } else {
            Cart cartItem = new Cart();
            cartItem.setUser(user);
            cartItem.setProduct(product);
            cartItem.setQuantity(1);
            cartRepo.save(cartItem);
        }

        redirectAttributes.addFlashAttribute("succMsg", "Product added to cart!");
        return "redirect:/products";
    }

    @GetMapping("/cart")
    public String showCart(Model model, HttpSession session) {
        User user = (User) session.getAttribute("currentuser");
        if (user == null) {
            return "redirect:/login";
        }
        List<Cart> cartItems = cartRepo.findByUser(user);
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Cart cart : cartItems) {
            BigDecimal price = cart.getProduct().getPrice().subtract(cart.getProduct().getDiscount());
            int quantity = cart.getQuantity();
            totalPrice = totalPrice.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("carts", cartItems);
        return "user/cart";
    }

    @GetMapping("/cartQuantityUpdate")
    public String updateCartQuantity(@RequestParam("sy") String symbol,
            @RequestParam("cid") int cartId,
            HttpSession session) {
        Cart cart = cartRepo.findById(cartId);

        if (cart != null) {
            int qty = cart.getQuantity();
            if ("in".equals(symbol)) {
                cart.setQuantity(qty + 1);
            } else if ("de".equals(symbol) && qty > 1) {
                cart.setQuantity(qty - 1);
            } else if ("de".equals(symbol) && qty == 1) {
                cartRepo.delete(cart); // Remove item if quantity is 1 and we decrease
                session.setAttribute("succMsg", "Item removed from cart");
                return "redirect:/user/cart";
            }
            cartRepo.save(cart);
            session.setAttribute("succMsg", "Quantity updated");
        } else {
            session.setAttribute("errorMsg", "Cart item not found");
        }

        return "redirect:/user/cart";
    }

    @GetMapping("/payment")
    public String showPaymentPage() {
        return "user/payment";
    }

}
