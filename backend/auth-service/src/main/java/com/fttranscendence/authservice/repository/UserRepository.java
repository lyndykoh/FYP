package com.fttranscendence.authservice.repository;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  List<User> findAllByRoleOrderByFullnameAscEmailAsc(UserRole role);
}
