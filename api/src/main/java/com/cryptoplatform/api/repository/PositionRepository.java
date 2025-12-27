package com.cryptoplatform.api.repository;

import com.cryptoplatform.api.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Position> findByAccountId(Long accountId);
}
