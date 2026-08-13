package ShoppingCart.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ShoppingCart.demo.model.User;



public interface UserRepository extends JpaRepository<User,Integer> {
    
    public User findById(int userId);

    public User findByEmail(String email);

    public List<User> findByRole(String role);

    public void deleteById(int id);
}
