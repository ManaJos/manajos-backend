package ch.manajos.manajos.services;

import org.springframework.stereotype.Service;

@Service
public class GlobalService {
    public String getWelcomeMessage() {
        return "Bienvenue sur notre application!";
    }

    public String getGlobal() {
        return "Bienvenue sur notre application!";
    }
}
