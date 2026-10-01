package com.portfolioanalyzer.repository;

import com.portfolioanalyzer.entity.ResumeSkillMatchEntity;
import com.portfolioanalyzer.entity.ScoreHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeSkillMatchRepository extends JpaRepository<ResumeSkillMatchEntity, Long> {

    List<ResumeSkillMatchEntity> findByScoreHistory(ScoreHistoryEntity scoreHistory);
}
