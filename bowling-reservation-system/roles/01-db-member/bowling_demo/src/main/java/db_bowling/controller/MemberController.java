package db_bowling.controller;

import db_bowling.domain.Member;
import db_bowling.service.MemberService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("members", memberService.getMembers(keyword));
        model.addAttribute("keyword", keyword);
        return "members/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("member", new Member());
        return "members/form";
    }

    @PostMapping
    public String create(@ModelAttribute Member member, RedirectAttributes rttr) {
        try {
            memberService.registerMember(member);
            return "redirect:/members";
        } catch (IllegalArgumentException e) {
            rttr.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/members/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberService.getMember(id));
        return "members/detail";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute Member member) {
        member.setMemberId(id);
        memberService.modifyMember(member);
        return "redirect:/members/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes rttr) {
        try {
            memberService.removeMember(id);
            return "redirect:/members";
        } catch (IllegalStateException e) {
            rttr.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/members/" + id;
        }
    }
}