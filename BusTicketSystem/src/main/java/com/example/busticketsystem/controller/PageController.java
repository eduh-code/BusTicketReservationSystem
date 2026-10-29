package com.example.busticketsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

@Controller
public class PageController {
    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/results")
    public String results(
            @RequestParam(defaultValue = "Dar es Salaam") String source,
            @RequestParam(defaultValue = "Dodoma") String destination,
            @RequestParam(defaultValue = "") String date,
            Model model) {
        model.addAttribute("source", source);
        model.addAttribute("destination", destination);
        model.addAttribute("date", date);
        return "results";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
