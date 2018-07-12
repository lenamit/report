package vn.com.fwd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.fwd.model.security.User;


public interface UserRepository extends JpaRepository<User, String> {
    User findByUsername(String username);
}

