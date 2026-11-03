package com.example.bowling.controller;

import com.example.bowling.repository.LaneRepository;
import com.example.bowling.service.LaneService;
import com.example.bowling.service.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lanes")
public class LaneController {
    private final LaneRepository lanes;
    private final LaneService service;
    public LaneController(LaneRepository lanes,LaneService service){this.lanes=lanes;this.service=service;}

    @GetMapping
    public String list(Model model){model.addAttribute("lanes",lanes.findAll());return "lanes/list";}

    @GetMapping("/{laneId}")
    public String detail(@PathVariable long laneId,Model model){
        model.addAttribute("lane",lanes.findOne(laneId));
        model.addAttribute("schedules",lanes.schedules(laneId));
        return "lanes/detail";
    }

    @PostMapping("/{laneId}/status")
    public String change(@PathVariable long laneId,@RequestParam String status,RedirectAttributes redirect){
        try {service.updateStatus(laneId,status);redirect.addFlashAttribute("message","시설 상태를 변경했습니다.");}
        catch(BusinessException | DataAccessException e){redirect.addFlashAttribute("error",friendly(e));}
        return "redirect:/lanes/"+laneId;
    }

    private String friendly(Exception e) {
        return e instanceof BusinessException ? e.getMessage() : "DB 처리 실패: 상태 제약조건과 연결을 확인하세요.";
    }
}
