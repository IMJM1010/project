package com.example.bowling.controller;
import com.example.bowling.repository.LaneRepository;
import com.example.bowling.service.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/lanes")
public class LaneController {
 private final LaneRepository lanes;private final LaneService service;
 public LaneController(LaneRepository lanes,LaneService service){this.lanes=lanes;this.service=service;}
 @GetMapping public String list(Model m){m.addAttribute("lanes",service.findLanes());return "lanes/list";}
 @GetMapping("/{id}") public String detail(@PathVariable long id,Model m){m.addAttribute("lane",lanes.findOne(id));m.addAttribute("schedules",lanes.schedules(id));m.addAttribute("lanes",service.findLanes());return "lanes/detail";}
 @PostMapping("/{id}/status") public String change(@PathVariable long id,@RequestParam String status,RedirectAttributes r){
  try{
   if("MAINTENANCE".equals(status)){var result=service.markMaintenance(id);r.addFlashAttribute("message","점검 처리: 재배정 "+result.replaced()+"건 / 실패 예약 "+result.failedReservations());}
   else if("AVAILABLE".equals(status)){service.restoreLane(id);r.addFlashAttribute("message","점검 완료");}
   else throw new BusinessException("허용되지 않은 상태 변경");
  }catch(BusinessException|DataAccessException e){r.addFlashAttribute("error",e instanceof BusinessException?e.getMessage():"DB 처리 실패");}
  return "redirect:/lanes/"+id;
 }
}
