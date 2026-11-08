package com.studyroombooker.backend.service;

import java.util.List;

import com.studyroombooker.backend.repository.ShiftRepository;
import com.studyroombooker.backend.repository.ShiftRepository.ShiftSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {
    private final ShiftRepository shifts;

    public ShiftService(ShiftRepository shifts) {
        this.shifts = shifts;
    }

    @Transactional(readOnly = true)
    public List<ShiftSummary> listShifts(String viewerEmail) {
        return shifts.findAll(viewerEmail);
    }
}
