package com.ridehailing.identityservice.repository;
import com.ridehailing.identityservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID;
public interface UserRepository extends JpaRepository<User,UUID>{Optional<User> findByEmail(String email);}
