package com.portfolioanalyzer.repository;

import com.portfolioanalyzer.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByGithubUsernameIgnoreCase(String githubUsername);

    boolean existsByGithubUsernameIgnoreCase(String githubUsername);
}
