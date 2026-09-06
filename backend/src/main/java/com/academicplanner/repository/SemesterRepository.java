package com.academicplanner.repository;

import com.academicplanner.entity.Semester;
import com.academicplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    List<Semester> findAllByUserOrderByStartDateDesc(User user);

    Optional<Semester> findByIdAndUser(Long id, User user);

    boolean existsByIdAndUser(Long id, User user);
}
