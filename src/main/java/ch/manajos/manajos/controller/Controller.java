package ch.manajos.manajos.controller;

import ch.manajos.manajos.services.GlobalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    private final GlobalService globalService;

    @Autowired
    public Controller(GlobalService globalService) {
        this.globalService = globalService;
    }

    @GetMapping("/welcome")
    public String welcome() {
        return globalService.getWelcomeMessage();
    }

}
