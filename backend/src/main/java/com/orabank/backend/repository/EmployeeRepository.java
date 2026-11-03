package com.orabank.backend.repository;

import com.orabank.backend.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByMatriculeIgnoreCase(String matricule);

    boolean existsByMatriculeIgnoreCase(String matricule);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMatriculeIgnoreCaseAndIdNot(String matricule, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
