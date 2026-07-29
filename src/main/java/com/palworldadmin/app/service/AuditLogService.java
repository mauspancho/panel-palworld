package com.palworldadmin.app.service;

import com.palworldadmin.app.entity.ActionStatus;
import com.palworldadmin.app.entity.AuditLog;
import com.palworldadmin.app.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository logs;

    public AuditLogService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void success(String actor, String target, String action, String description) {
        write(actor, target, action, ActionStatus.SUCCESS, description);
    }

    @Transactional
    public void failed(String actor, String target, String action, String description) {
        write(actor, target, action, ActionStatus.FAILED, description);
    }

    public Page<AuditLog> recent(int page, int size) {
        int safeSize = switch (size) {
            case 10, 50, 100 -> size;
            default -> 10;
        };
        return logs.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), safeSize));
    }

    private void write(String actor, String target, String action, ActionStatus status, String description) {
        AuditLog log = new AuditLog();
        log.setActorUsername(clean(actor));
        log.setTargetUsername(clean(target));
        log.setAction(action);
        log.setStatus(status);
        log.setDescription(description == null ? "" : description);
        logs.save(log);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }
}
