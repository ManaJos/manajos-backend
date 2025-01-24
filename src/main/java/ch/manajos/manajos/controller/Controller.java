package ch.manajos.manajos.controller;

import ch.manajos.manajos.services.GlobalService;
import ch.manajos.manajos.services.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    private final GlobalService globalService;
    private final NewsService newsService;

    @Autowired
    public Controller(GlobalService globalService, NewsService newsService) {
        this.globalService = globalService;
        this.newsService = newsService;
    }

    @GetMapping("/welcome")
    public String welcome() {
        return globalService.getWelcomeMessage();
    }

    @GetMapping("/news")
    public String news() {return newsService.getNews();}

}
