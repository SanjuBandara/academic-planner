package com.academicplanner.service;

import com.academicplanner.dto.module.ModuleRequest;
import com.academicplanner.dto.module.ModuleResponse;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Semester;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final SemesterService semesterService;

    @Transactional(readOnly = true)
    public List<ModuleResponse> getAllBySemester(Long semesterId, User user) {
        // Ownership check via semester lookup
        semesterService.getEntityById(semesterId, user);
        return moduleRepository.findAllBySemesterIdAndUserId(semesterId, user.getId())
                .stream().map(ModuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> getAllForUser(User user) {
        return moduleRepository.findAllByUserId(user.getId())
                .stream().map(ModuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse getById(Long id, User user) {
        Module module = moduleRepository.findByIdAndSemester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + id));
        return ModuleResponse.from(module);
    }

    @Transactional
    public ModuleResponse create(Long semesterId, ModuleRequest request, User user) {
        Semester semester = semesterService.getEntityById(semesterId, user);

        // Duplicate code check within the same semester
        if (moduleRepository.existsBySemesterAndCode(semester, request.code().trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Module with code '" + request.code() + "' already exists in this semester.");
        }

        Module module = Module.builder()
                .semester(semester)
                .code(request.code().trim().toUpperCase())
                .name(request.name())
                .credits(request.credits())
                .description(request.description())
                .build();

        return ModuleResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public ModuleResponse update(Long id, ModuleRequest request, User user) {
        Module module = moduleRepository.findByIdAndSemester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + id));

        String newCode = request.code().trim().toUpperCase();
        // Allow same code if it belongs to this module (not changed), but block duplicates otherwise
        if (!module.getCode().equals(newCode)
                && moduleRepository.existsBySemesterAndCodeAndIdNot(module.getSemester(), newCode, id)) {
            throw new IllegalArgumentException(
                    "Module with code '" + newCode + "' already exists in this semester.");
        }

        module.setCode(newCode);
        module.setName(request.name());
        module.setCredits(request.credits());
        module.setDescription(request.description());

        return ModuleResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public void delete(Long id, User user) {
        Module module = moduleRepository.findByIdAndSemester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + id));
        moduleRepository.delete(module);
    }

    /** Returns raw entity for use by other services. */
    @Transactional(readOnly = true)
    public Module getEntityById(Long id, User user) {
        return moduleRepository.findByIdAndSemester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + id));
    }
}
