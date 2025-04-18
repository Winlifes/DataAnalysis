package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.GameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {
    // Spring Data JPA provides basic CRUD operations automatically
}