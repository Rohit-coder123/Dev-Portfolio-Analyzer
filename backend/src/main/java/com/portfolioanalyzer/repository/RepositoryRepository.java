package com.portfolioanalyzer.repository;

import com.portfolioanalyzer.entity.RepositoryEntity;
import com.portfolioanalyzer.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepositoryRepository extends JpaRepository<RepositoryEntity, Long> {

    List<RepositoryEntity> findByUser(UserEntity user);

    void deleteByUser(UserEntity user);
}
