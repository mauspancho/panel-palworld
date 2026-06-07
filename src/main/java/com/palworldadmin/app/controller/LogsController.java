package com.palworldadmin.app.controller;

import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogsController {
    private final PalworldServerService servers;
    private final ActionLogService actionLogs;

    public LogsController(PalworldServerService servers, ActionLogService actionLogs) {
        this.servers = servers;
        this.actionLogs = actionLogs;
    }

    @GetMapping("/servers/{id}/logs")
    public String logs(@PathVariable Long id, @RequestParam(defaultValue = "200") int lines, Model model) {
        var server = servers.get(id);
        var result = servers.logs(id, lines);
        model.addAttribute("server", server);
        model.addAttribute("lines", lines);
        model.addAttribute("serverLogs", result.combinedOutput());
        model.addAttribute("internalLogs", actionLogs.recent(server));
        return "logs";
    }
}
