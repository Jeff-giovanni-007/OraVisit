package com.orabank.backend.security;

import com.orabank.backend.entity.Employee;
import com.orabank.backend.repository.EmployeeRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = "authError";

    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String matricule = jwtService.parseAccessToken(header.substring(7).trim()).getSubject();
            Optional<Employee> employee = employeeRepository.findByMatriculeIgnoreCase(matricule)
                    .filter(candidate -> candidate.getRole().hasApplicationAccess());
            if (employee.isPresent()) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        employee.get(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + employee.get().getRole().name()))));
            } else {
                request.setAttribute(ERROR_ATTRIBUTE, "TOKEN_INVALID");
            }
        } catch (ExpiredJwtException expired) {
            request.setAttribute(ERROR_ATTRIBUTE, "TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException invalid) {
            request.setAttribute(ERROR_ATTRIBUTE, "TOKEN_INVALID");
        }

        chain.doFilter(request, response);
    }
}
