package ShoppingCart.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String name;
    private String imageName;
    private Boolean isActive;

    public Category() {}

    public Category(int id, String name, String imageName, Boolean isActive) {
        this.id = id;
        this.name = name;
        this.imageName = imageName;
        this.isActive = isActive;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImageName() {
        return imageName;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
