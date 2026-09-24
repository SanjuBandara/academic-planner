package com.academicplanner.repository;

import com.academicplanner.entity.AiChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, Long> {

    @Query("SELECT h FROM AiChatHistory h WHERE h.user.id = :userId ORDER BY h.timestamp DESC")
    List<AiChatHistory> findRecentHistoryByUserId(Long userId, Pageable pageable);

}
