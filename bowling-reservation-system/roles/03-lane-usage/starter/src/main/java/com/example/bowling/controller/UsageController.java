package com.example.bowling.controller;
import com.example.bowling.repository.UsageRepository;
import com.example.bowling.service.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/usages")
public class UsageController {
 private final UsageRepository repo;private final UsageService service;
 public UsageController(UsageRepository repo,UsageService service){this.repo=repo;this.service=service;}
 @GetMapping public String list(Model m){m.addAttribute("usages",repo.listAll());return "usages/list";}
 private String action(Runnable f,String redirect,RedirectAttributes r){try{f.run();r.addFlashAttribute("message","처리 완료");}catch(BusinessException|DataAccessException e){r.addFlashAttribute("error",e instanceof BusinessException?e.getMessage():"DB 처리 실패");}return "redirect:"+redirect;}
 @PostMapping("/start") public String start(@RequestParam long reservationLaneId,@RequestParam long laneId,RedirectAttributes r){return action(()->service.start(reservationLaneId),"/lanes/"+laneId,r);}
 @PostMapping("/{id}/end") public String end(@PathVariable long id,RedirectAttributes r){return action(()->service.finish(id),"/usages",r);}
 @PostMapping("/unused") public String unused(@RequestParam long reservationLaneId,@RequestParam long laneId,RedirectAttributes r){return action(()->service.markUnused(reservationLaneId),"/lanes/"+laneId,r);}
 @PostMapping("/attendance") public String attendance(@RequestParam long reservationId,@RequestParam int actualPersonCount,@RequestParam long laneId,RedirectAttributes r){return action(()->service.recordAttendance(reservationId,actualPersonCount),"/lanes/"+laneId,r);}
 @PostMapping("/finish-reservation") public String finish(@RequestParam long reservationId,@RequestParam long laneId,RedirectAttributes r){return action(()->service.finishReservation(reservationId),"/lanes/"+laneId,r);}
 @PostMapping("/extend") public String extend(@RequestParam long reservationId,@RequestParam int minutes,@RequestParam long laneId,RedirectAttributes r){return action(()->service.extendReservation(reservationId,minutes),"/lanes/"+laneId,r);}
 @PostMapping("/assign-lane") public String assign(@RequestParam long reservationId,@RequestParam long newLaneId,@RequestParam long laneId,RedirectAttributes r){return action(()->service.assignAdditionalLane(reservationId,newLaneId),"/lanes/"+laneId,r);}
 @PostMapping("/add-lane") public String add(@RequestParam long reservationId,@RequestParam long newLaneId,@RequestParam long laneId,RedirectAttributes r){return action(()->service.addLane(reservationId,newLaneId),"/lanes/"+laneId,r);}
}
