package ShoppingCart.demo.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import ShoppingCart.demo.Repository.CategoryRepository;
import ShoppingCart.demo.Repository.UserRepository;
import ShoppingCart.demo.model.Category;
import ShoppingCart.demo.model.Product;
import ShoppingCart.demo.model.User;
import ShoppingCart.demo.service.CategoryService;
import ShoppingCart.demo.service.ProductService;
import ShoppingCart.demo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService prodService;

    @Autowired
    private CategoryRepository categoryRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String index() {
        return "admin/index";
    }

    @GetMapping("/category")
    public String addCategory() {
        return "admin/category";
    }

    @GetMapping("/edit-category/{id}")
    public String editCategory(@PathVariable int id, Model m) {
        Category cate = categoryService.getCategoryById(id);
        if (cate == null) {
            throw new RuntimeException("Category Not Found...");
        }
        m.addAttribute("category", cate);
        return "admin/edit_category";
    }

    @PostMapping("/deleteCategory/{id}")
    public String deleteCategory(@PathVariable int id, HttpSession session) {
        try {
            categoryRepo.deleteById(id);
            session.setAttribute("succMsg", "Category has been deleted Successfully...");

        } catch (Exception e) {
            session.setAttribute("errorMsg", "error comes to delete category : " + e.getMessage());
        }

        return "redirect:/admin/view-category";
    }

    @PostMapping("/updateCategory")
    public String updateCategory(@ModelAttribute Category m,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        try {
            Category existing = categoryRepo.findById(m.getId()).orElse(null);

            if (existing == null) {
                session.setAttribute("errorMsg", "Category not found!");
                return "redirect:/admin/view-category";
            }

            existing.setName(m.getName());
            existing.setIsActive(m.getIsActive());

            if (!file.isEmpty()) {
                String fileName = file.getOriginalFilename();
                existing.setImageName(fileName);

                Path path = Paths.get("src/main/resources/static/img/category_img/" + fileName);
                Files.write(path, file.getBytes());
            }

            categoryRepo.save(existing);
            session.setAttribute("succMsg", "Category Updated Successfully");

        } catch (Exception e) {
            e.printStackTrace();
            session.setAttribute("errorMsg", "Something went wrong while updating.");
        }

        return "redirect:/admin/view-category";
    }

    @GetMapping("/view-category")
    public String category(Model m) {
        m.addAttribute("categories", categoryService.getAllCategory());
        return "admin/view_category";
    }

    @PostMapping("/saveCategory")
    public String saveCategory(@ModelAttribute Category category, @RequestParam("file") MultipartFile file,
            HttpSession session) throws IOException {

        boolean existcat = categoryService.existCategory(category.getName());

        if (existcat) {
            session.setAttribute("errorMsg", "Category already exists...");
            return "redirect:/admin/category";
        }

        // Set default image name
        String imgName = file != null && !file.isEmpty() ? file.getOriginalFilename() : "default.png";
        category.setImageName(imgName);

        // Save the category first
        Category savedCategory = categoryService.saveCategory(category);
        if (savedCategory == null) {
            session.setAttribute("errorMsg", "Internal server error...");
            return "redirect:/admin/category";
        }

        // If file is not empty, save it to desired directory
        if (file != null && !file.isEmpty()) {
            String uploadDir = "C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/category_img";
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                uploadPath.mkdirs();
            }

            Path path = Paths.get(uploadDir + File.separator + file.getOriginalFilename());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        }

        session.setAttribute("succMsg", "Category has been saved successfully...");
        return "redirect:/admin/view-category";
    }

    @GetMapping("/addProduct")
    public String addProduct(Model m) {
        List<Category> categories = categoryService.getAllCategory();
        m.addAttribute("categories", categories);
        return "admin/add_product";
    }

    @PostMapping("/saveProduct")
    public String saveProduct(@ModelAttribute Product prod, HttpSession session, MultipartFile file) {

        try {
            boolean existprod = prodService.existsByNameAndBrand(prod.getName(), prod.getBrand());
            if (existprod) {
                session.setAttribute("errorMsg", "Product already exists with this name and brand.");
                return "redirect:/admin/addProduct";
            }

            if (!file.isEmpty()) {
                String fileName = file.getOriginalFilename();
                prod.setImageName(fileName);

                String uploadDir = "C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/product_img";
                File uploadPath = new File(uploadDir);
                if (!uploadPath.exists()) {
                    uploadPath.mkdirs();
                }

                Path path = Paths.get(uploadDir + File.separator + fileName);

                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            } else {
                prod.setImageName("default.png");
            }

            prodService.saveProduct(prod);
            session.setAttribute("succMsg", "Product added successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            session.setAttribute("errorMsg", "Something went wrong in adding product: " + e.getMessage());
        }

        return "redirect:/admin/addProduct";
    }

    @GetMapping("/products")
    public String viewProduct(Model m) {
        List<Product> products = prodService.getAllProducts();
        m.addAttribute("products", products);
        return "admin/products";
    }

    @GetMapping("/updateProduct/{id}")
    public String editProduct(@PathVariable int id, Model m) {
        Product item = prodService.getProductById(id);
        List<Category> cate = categoryService.getAllCategory();
        if (item == null) {
            throw new RuntimeException("Product Not Found...");
        }
        m.addAttribute("product", item);
        m.addAttribute("categories", cate);
        return "admin/edit_product";
    }

    @PostMapping("/updateProduct")
    public String updateProduct(@ModelAttribute Product prod,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        try {

            Product existingProd = prodService.getProductById(prod.getId());
            if (existingProd == null) {
                session.setAttribute("errorMsg", "Product not found.");
                return "redirect:/admin/products";
            }

            // Update basic fields
            existingProd.setName(prod.getName());
            existingProd.setDescription(prod.getDescription());
            existingProd.setPrice(prod.getPrice());
            existingProd.setDiscount(prod.getDiscount());
            existingProd.setQuantity(prod.getQuantity());
            existingProd.setBrand(prod.getBrand());
            existingProd.setActive(prod.isActive());
            existingProd.setCategory(prod.getCategory());

            if (!file.isEmpty()) {
                String fileName = file.getOriginalFilename();
                existingProd.setImageName(fileName);

                String uploadDir = "C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/product_img";
                File uploadPath = new File(uploadDir);
                if (!uploadPath.exists()) {
                    uploadPath.mkdirs();
                }

                Path path = Paths.get(uploadDir + File.separator + fileName);
                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            }

            // Save updated product
            prodService.saveProduct(existingProd);

            session.setAttribute("succMsg", "Product updated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            session.setAttribute("errorMsg", "Something went wrong while updating product: " + e.getMessage());
        }

        return "redirect:/admin/products";
    }

    @GetMapping("/addAdmin")
    public String showAddAdminForm() {
        return "admin/add_admin";
    }

    @PostMapping("/saveAdmin")
    public String saveAdmin(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam("img") MultipartFile img,
            HttpServletRequest request) {
        return userService.saveAdmin(name, email, password, img, request);
    }

    @GetMapping("/viewAdmin")
    public String viewAdmins(Model model) {
        List<User> admins = userService.getAllAdmins();
        model.addAttribute("admins", admins);
        return "admin/view_admins";
    }

    @GetMapping("/viewUsers")
    public String viewAllUsers(Model model) {
        List<User> users = userRepo.findByRole("USER");
        model.addAttribute("users", users);
        return "view_users"; // this will map to view_user.html
    }

    @PostMapping("/deleteAdmin/{id}")
    public String deleteAdmin(@PathVariable int id, HttpSession session) {
        try {
            userRepo.deleteById(id);
            session.setAttribute("succMsg", "Admin has been deleted Successfully...");

        } catch (Exception e) {
            session.setAttribute("errorMsg", "error comes to delete admin : " + e.getMessage());
        }

        return "redirect:/admin/viewAdmin";
    }

}