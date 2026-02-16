package com.farm.sales.repository;

import com.farm.sales.model.User;
import com.farm.sales.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  List<User> findAllByRoleOrderByFullNameAsc(Role role);
}
