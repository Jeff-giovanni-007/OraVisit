package com.orabank.backend.service;

import com.orabank.backend.dto.DashboardResponse;
import com.orabank.backend.entity.VisitStatus;
import com.orabank.backend.repository.EmployeeRepository;
import com.orabank.backend.repository.VisitRepository;
import com.orabank.backend.repository.VisitorRepository;
import com.orabank.backend.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VisitRepository visitRepository;
    private final VisitorRepository visitorRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse load() {
        LocalDate today = LocalDate.now(clock);
        Instant startOfToday = today.atStartOfDay(clock.getZone()).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();

        Long agentId = CurrentUser.restrictedAgentId().orElse(null);
        if (agentId != null) {
            return new DashboardResponse(
                    visitRepository.countByRegisteredByIdAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
                            agentId, startOfToday, startOfTomorrow),
                    visitRepository.countByRegisteredByIdAndStatus(agentId, VisitStatus.EN_COURS),
                    visitRepository.countByRegisteredByIdAndStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
                            agentId, VisitStatus.TERMINEE, startOfToday, startOfTomorrow),
                    visitRepository.countByRegisteredByIdAndStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
                            agentId, VisitStatus.ANNULEE, startOfToday, startOfTomorrow),
                    null, null, null);
        }
        return new DashboardResponse(
                visitRepository.countByCheckInAtGreaterThanEqualAndCheckInAtLessThan(startOfToday, startOfTomorrow),
                visitRepository.countByStatus(VisitStatus.EN_COURS),
                visitRepository.countByStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
                        VisitStatus.TERMINEE, startOfToday, startOfTomorrow),
                visitRepository.countByStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
                        VisitStatus.ANNULEE, startOfToday, startOfTomorrow),
                visitorRepository.count(),
                visitRepository.count(),
                employeeRepository.count());
    }
}
