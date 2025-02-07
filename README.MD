# ManaJos backend

This project is a Spring Boot application that integrates with the Steam API to fetch game data and user information. It retrieves the top games, game details, and user information from Steam and exposes them via RESTful endpoints.

> **Note:** You must provide your Steam API key by setting the property `steam.api.key` (for example, in the `src/main/resources/application.properties` file).

## Prerequisites

- **Java 17**  
  Make sure you have Java 17 installed. You can check your Java version with:
  java -version

- **Maven**  
  This project uses Maven to manage dependencies and build the project. You can verify your Maven installation with:
  mvn -v

## Installation

1. **Clone the repository:**

   git clone https://github.com/yourusername/your-repo.git
   cd your-repo

2. **Build the project using Maven:**

   mvn clean install

## Running the Application

Run the application with the following command. This will start the Spring Boot application on port 8080:

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080"

Once the application starts, you can access it at: http://localhost:8080.

## Available API Endpoints

Below are the endpoints currently exposed by the application:

### 1. GET `/steam/top-games`

- **Description:**  
  Retrieves a list of the top games with basic details such as app ID, player count, and rank. The service enriches the game data with the game name by making an additional call to the Steam store API.

- **Example Request:**

  curl http://localhost:8080/steam/top-games

### 2. GET `/steam/game-details/{appId}`

- **Description:**  
  Retrieves detailed information for a specific game.

- **Path Parameter:**
    - `appId` - The Steam application ID of the game.

- **Example Request:**

  curl http://localhost:8080/steam/game-details/730

### 3. GET `/steam/user-info/{steamId64}`

- **Description:**  
  Retrieves information about a Steam user.

- **Path Parameter:**
    - `steamId64` - The 64-bit Steam ID of the user.

- **Example Request:**

  curl http://localhost:8080/steam/user-info/76561198000000000

## Project Details

- **Caching:**  
  The application caches the results for top games and game details to reduce the number of external API calls.

- **External API Calls:**  
  The service calls the following external Steam API endpoints:
    - `/ISteamChartsService/GetMostPlayedGames/v1/`
    - `https://store.steampowered.com/api/appdetails?appids={appId}`
    - `/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={id}`

- **WebClient & Reactive Programming:**  
  It leverages Spring WebFlux’s WebClient for making asynchronous HTTP requests to the Steam API.

## Configuration

Ensure that your Steam API key is set in the application configuration. For example, in src/main/resources/application.properties:

steam.api.key=YOUR_STEAM_API_KEY

## License

This project is licensed under the MIT License.
