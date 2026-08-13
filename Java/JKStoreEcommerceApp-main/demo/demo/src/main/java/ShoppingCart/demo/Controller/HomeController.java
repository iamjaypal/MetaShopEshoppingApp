package ShoppingCart.demo.Controller;

import ShoppingCart.demo.Repository.CategoryRepository;
import ShoppingCart.demo.Repository.ProductRepository;
import ShoppingCart.demo.Repository.UserRepository;
import ShoppingCart.demo.model.Category;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;
import ShoppingCart.demo.service.CategoryService;
import ShoppingCart.demo.service.ProductService;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class HomeController {

   @Autowired
   ProductService prodService;

   @Autowired
   CategoryService cateService;

   @Autowired
   CategoryRepository cateRepo;

   @Autowired
   ProductRepository prodRepo;

   @Autowired
   UserRepository userRepo;

   @ModelAttribute
   public void commonData(Model model) {
      List<Category> categories = cateRepo.findAll();
      model.addAttribute("category", categories);
   }

   @GetMapping("/")
   public String home(Model model, Principal principal,
         @RequestParam(value = "category", required = false) String categoryName) {


      if (principal != null) {
         User user = userRepo.findByEmail(principal.getName());
         model.addAttribute("user", user);
      }


      List<Category> categories = cateRepo.findAll();
      List<Product> products;
      if (categoryName != null) {
         products = prodRepo.findByCategoryName(categoryName);
      } else {
         products = prodRepo.findAll();
      }

      
      model.addAttribute("category", categories);
      model.addAttribute("products", products);

      return "index";
   }

   @GetMapping("/login")
   public String login() {
      return "login";
   }

   @GetMapping("/logout")
   public String logout(HttpSession session) {
      session.invalidate();
      return "redirect:/login";
   }

   @PostMapping("/loginUser")
   public String loginUser(@RequestParam String email, @RequestParam String password, HttpSession session) {

      User user = userRepo.findByEmail(email);

      if (user == null) {
         session.setAttribute("errorMsg", "User not found...");
         return "redirect :/login";
      }
      if (!user.getPassword().equals(password)) {
         session.setAttribute("errorMsg", "Invalid password...Try Again");
         return "redirect:/login";
      }
      if (!user.isEnabled()) {
         session.setAttribute("errorMsg", "User is not Enabled...");
      }

      session.setAttribute("currentuser", user);
      return "redirect:/";

   }

   @GetMapping("/register")
   public String register() {
      return "register";
   }

   @PostMapping("/saveUser")
   public String saveUser(@RequestParam("name") String name,
         @RequestParam("email") String email,
         @RequestParam("password") String password,
         @RequestParam("confirmpassword") String confirmPassword,
         @RequestParam("img") MultipartFile file,
         HttpSession session) {

      try {
         if (!password.equals(confirmPassword)) {
            session.setAttribute("errorMsg", "Passwords do not match");
            return "redirect:/register";
         }


          // Save image
         String fileName = file.getOriginalFilename();
         String uploadDir = "C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/profile_img";
         File uploadPath = new File(uploadDir);
         if (!uploadPath.exists()) {
            uploadPath.mkdirs(); // Create directory if it doesn't exist
         }
         Path path = Paths.get(uploadPath + File.separator + fileName);
         Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

         User user = new User();
         user.setName(name);
         user.setEmail(email);
         user.setPassword(password); // Note: Use password encoder in real apps
         user.setRole("USER");
         user.setEnabled(true);
         user.setProfileImage(fileName); // You'll need to add this field in User.java

         userRepo.save(user);
         session.setAttribute("succMsg", "Registered successfully");
         return "redirect:/register";

      } catch (Exception e) {
         session.setAttribute("errorMsg", "Something went wrong");
         e.printStackTrace();
         return "redirect:/register";
      }
   }

   @GetMapping("/products")
   public String products(@RequestParam(value = "category", required = false) String category,
         @RequestParam(value = "ch", required = false) String searchKeyword,
         Model m) {
      List<Product> productList;

      if (category != null && !category.isEmpty()) {
         productList = prodService.getProductsByCategory(category);
      } else if (searchKeyword != null && !searchKeyword.isEmpty()) {
         productList = prodService.findByNameContainsIgnoreCase(searchKeyword);
      } else {
         productList = prodService.getAllProducts();
      }

      m.addAttribute("products", productList);
      m.addAttribute("productsSize", productList.size());
      m.addAttribute("totalElements", productList.size());

      // No pagination yet
      m.addAttribute("pageNo", 0);
      m.addAttribute("totalPages", 1);
      m.addAttribute("isFirst", true);
      m.addAttribute("isLast", true);

      // For category highlight
      m.addAttribute("categories", cateService.getAllCategory());
      m.addAttribute("paramValue", category != null ? category : "");

      return "product";
   }

   @GetMapping("/product/{id}")
   public String product(@PathVariable int id, Model m, Principal p) {
      Product productById = prodService.getProductById(id);
      m.addAttribute("product", productById);
      return "view_product";
   }

}
