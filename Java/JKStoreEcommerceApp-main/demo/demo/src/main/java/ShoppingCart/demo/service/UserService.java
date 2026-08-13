package ShoppingCart.demo.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ShoppingCart.demo.Repository.UserRepository;
import ShoppingCart.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;


    public String saveAdmin(String name, String email, String password, MultipartFile imgFile, HttpServletRequest request) {
        if (userRepository.findByEmail(email)!=null) {
            request.getSession().setAttribute("errorMsg", "Email already registered.");
            return "redirect:/admin/addAdmin";
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole("ADMIN");

        // Save image
        if (!imgFile.isEmpty()) {
            String fileName = UUID.randomUUID().toString() + "_" + imgFile.getOriginalFilename();
            try {
                Path path = Paths.get("src/main/resources/static/img/" + fileName);
                Files.copy(imgFile.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                user.setProfileImage(fileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        userRepository.save(user);
        request.getSession().setAttribute("succMsg", "Admin registered successfully!");
        return "redirect:/admin/addAdmin";
    }


    public List<User> getAllAdmins() {
        return userRepository.findByRole("ADMIN");
    }
}
