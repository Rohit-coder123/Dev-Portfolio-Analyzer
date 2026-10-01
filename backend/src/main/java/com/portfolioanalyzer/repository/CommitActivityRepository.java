package com.portfolioanalyzer.repository;

import com.portfolioanalyzer.entity.CommitActivityEntity;
import com.portfolioanalyzer.entity.ScoreHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitActivityRepository extends JpaRepository<CommitActivityEntity, Long> {

    List<CommitActivityEntity> findByScoreHistoryOrderByWeekStartDateAsc(ScoreHistoryEntity scoreHistory);
}
