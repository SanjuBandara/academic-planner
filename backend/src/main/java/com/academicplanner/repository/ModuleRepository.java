package com.academicplanner.repository;

import com.academicplanner.entity.Module;
import com.academicplanner.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findAllBySemesterOrderByNameAsc(Semester semester);

    Optional<Module> findByIdAndSemester_User_Id(Long id, Long userId);

    boolean existsBySemesterAndCode(Semester semester, String code);

    boolean existsBySemesterAndCodeAndIdNot(Semester semester, String code, Long id);

    /** All modules for a student across all semesters (for planning). */
    @Query("SELECT m FROM Module m WHERE m.semester.user.id = :userId ORDER BY m.semester.startDate DESC, m.name ASC")
    List<Module> findAllByUserId(@Param("userId") Long userId);

    /** All modules in a specific semester that belongs to the given user. */
    @Query("SELECT m FROM Module m WHERE m.semester.id = :semesterId AND m.semester.user.id = :userId ORDER BY m.name ASC")
    List<Module> findAllBySemesterIdAndUserId(@Param("semesterId") Long semesterId, @Param("userId") Long userId);
}
