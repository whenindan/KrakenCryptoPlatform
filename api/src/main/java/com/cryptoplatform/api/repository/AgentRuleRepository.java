package com.cryptoplatform.api.repository;

import com.cryptoplatform.api.model.AgentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRuleRepository extends JpaRepository<AgentRule, Long> {
    List<AgentRule> findByUserIdAndIsActive(Long userId, Boolean isActive);
    List<AgentRule> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AgentRule> findByIsActive(Boolean isActive);
}
