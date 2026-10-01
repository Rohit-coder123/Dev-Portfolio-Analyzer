package com.portfolioanalyzer.repository;

import com.portfolioanalyzer.entity.ScoreHistoryEntity;
import com.portfolioanalyzer.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreHistoryRepository extends JpaRepository<ScoreHistoryEntity, Long> {

    List<ScoreHistoryEntity> findByUserOrderByCalculatedAtDesc(UserEntity user);
}
