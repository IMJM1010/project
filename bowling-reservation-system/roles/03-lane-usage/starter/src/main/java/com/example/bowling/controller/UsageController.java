package com.example.bowling.controller;

import com.example.bowling.repository.UsageRepository;
import com.example.bowling.service.UsageService;
import com.example.bowling.service.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usages")
public class UsageController {
    private final UsageRepository usages;
    private final UsageService service;
    public UsageController(UsageRepository usages,UsageService service){this.usages=usages;this.service=service;}

    @GetMapping
    public String list(Model model){model.addAttribute("usages",usages.listAll());return "usages/list";}

    @PostMapping("/start")
    public String start(@RequestParam long reservationId,@RequestParam long laneId,RedirectAttributes redirect){
        try {
            long usageId=service.start(reservationId,laneId);
            redirect.addFlashAttribute("message","이용을 시작했습니다. 기록번호: "+usageId);
        } catch(BusinessException | DataAccessException e) { redirect.addFlashAttribute("error",friendly(e)); }
        return "redirect:/lanes/"+laneId;
    }

    @PostMapping("/{usageId}/end")
    public String end(@PathVariable long usageId,RedirectAttributes redirect){
        try { service.finish(usageId);redirect.addFlashAttribute("message","이용을 종료했습니다."); }
        catch(BusinessException | DataAccessException e) { redirect.addFlashAttribute("error",friendly(e)); }
        return "redirect:/usages";
    }

    private String friendly(Exception e){
        return e instanceof BusinessException ? e.getMessage() : "DB 저장 오류: PK/FK/CHECK 제약조건을 확인하세요.";
    }
}
