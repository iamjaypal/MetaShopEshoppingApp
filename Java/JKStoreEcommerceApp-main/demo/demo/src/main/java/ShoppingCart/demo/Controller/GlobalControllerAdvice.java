package ShoppingCart.demo.Controller;

import ShoppingCart.demo.Repository.CategoryRepository;
import ShoppingCart.demo.model.Category;
import ShoppingCart.demo.model.User;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private CategoryRepository cateRepo;


    @ModelAttribute
    public void globalData(Model model, HttpSession session) {
        List<Category> categories = cateRepo.findAll();
        model.addAttribute("category", categories);

        // Get user from session
        User user = (User) session.getAttribute("currentuser");
        if (user != null) {
            model.addAttribute("user", user);
        }
    }

}
