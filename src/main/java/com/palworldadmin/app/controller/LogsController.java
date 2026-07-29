package com.palworldadmin.app.controller;

import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.ServerLogFilterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogsController {
    private final PalworldServerService servers;
    private final ActionLogService actionLogs;
    private final ServerLogFilterService serverLogFilter;

    public LogsController(PalworldServerService servers, ActionLogService actionLogs, ServerLogFilterService serverLogFilter) {
        this.servers = servers;
        this.actionLogs = actionLogs;
        this.serverLogFilter = serverLogFilter;
    }

    @GetMapping("/servers/{id}/logs")
    public String logs(@PathVariable Long id, @RequestParam(defaultValue = "200") int lines, Model model) {
        var server = servers.get(id);
        int safeLines = Math.max(1, Math.min(lines, 1000));
        var result = servers.logs(id, rawLogLines(safeLines));
        model.addAttribute("server", server);
        model.addAttribute("lines", safeLines);
        model.addAttribute("serverLogs", serverLogFilter.compactRconNoiseAndTail(result.combinedOutput(), safeLines));
        model.addAttribute("internalLogs", actionLogs.recent(server));
        return "logs";
    }

    private int rawLogLines(int visibleLines) {
        return Math.min(10000, Math.max(visibleLines, visibleLines * 10));
    }
}
