package ch.manajos.manajos.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GlobalService {

    private final WebClient webClient;

    @Autowired
    public GlobalService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String callExternalApi() {
        String apiUrl = "https://api.exemple.com/data"; // Remplacez par l'URL appropriée
        return webClient.get()
                .uri(apiUrl) // URL de l'API
                .retrieve() // Récupération de la réponse
                .bodyToMono(String.class) // Conversion du corps en String
                .block(); // Bloque et retourne le résultat pour une application synchrone

    }

}
