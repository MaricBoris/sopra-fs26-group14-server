# StoryWars

---

## Table of Contents
1. [Introduction](#1-introduction)
2. [Technologies Used](#2-technologies-used)
3. [High-Level Components](#3-high-level-components)
4. [Launch & Deployment](#4-launch--deployment)
5. [Roadmap](#5-roadmap)
6. [Authors and Acknowledgment](#6-authors-and-acknowledgment)
7. [License](#7-license)

---

## 1. Introduction
StoryWars is a 3-player online narrative game with 2 writers and a judge. Each writer is secretly assigned a genre and must steer a shared story towards it for the duration of the game whilst the opponent is pulling it in the opposite direction. The judge, who is in charge of determining the winner, interferes during the game by forcing quotes into writers' turns and using his ability to reduce a writer's time.

### Goal
StoryWars relies on the basic idea of a game in which 2 opposing writers, who have been assigned different genres, attempt to one up one another: the writers thus engage in a narrative duel contributing to a single story in a round-based manner while trying to win the judge’s vote.

### Motivation
The core motivation of our game is to provide for a fun writing exercise that has the potential to engage its players through absurd and silly moments, whilst retaining a certain level of pressure provided by time constraints, which are only made worse by the presence of a judge capable of influencing the pace of the game by demanding the incorporation of specific quotes into any of the player’s writings.

### How It Works
* 3 players: 2 writers and 1 judge
* Genres: each writer is secretly assigned a genre
* Shared theme: the common story setting or hook that both writers must anchor their story to
* Turns: writers alternate adding to a single shared story, each trying to pull the narrative toward their designated genre
* Quotes: the judge can assign a random ZenQuotes quote to either writer at any time, the writer must then incorporate it within 2 of their own turns or face pontetial consequences
* Winning: whoever steered the story most convincingly toward their genre (in the judge's view) wins

---

## 2. Technologies Used

* **Backend Framework:** Java 17+ / Spring Boot
* **Build System & Dependency Management:** Gradle
* **Database & Persistence:** Spring Data JPA / Hibernate (with H2 In-Memory Database)
* **Testing Suite:** JUnit 5 / Mockito / Spring Boot Test
* **Data Transformation:** MapStruct (DTO Mapping)

### There but not being used:
* **Real-Time Data Streaming:** Server-Sent Events (SSE) via Spring `SseEmitter`
    * **Status:** Implemented but currently inactive/deprecated.
    * **Reason:** During Milestone 3, we briefly switched from polling to Server-Sent Events (SSE) to improve the live experience for users and to reliably detect players leaving the game without using the explicit exit button. While this architecture worked well in local development environments, it turned out unreliable when deployed to Google App Engine. The upstream reverse proxies in front of the App Engine instances do not handle long-lived streaming responses well, causing unintended buffering and timeout issues. We considered WebSockets as an alternative architectural pattern, but they require migrating to Google App Engine Flexible and that is not free. We therefore switched back to polling, which works reliably through the proxy layer.

---

## 3. High-Level Components

### Component 1: REST API based Controllers
Mainly act as the interface between frontend and the logic handled by the services. The controllers handle the HTTP requests and the mapping of the payloads with a DTO which are passed to the services if needed and vice versa converting the needed data given by the services into payloads that are sent back to the client.

One Controller example:
[`GameController.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/GameController.java)

### Component 2: Entity Model
The Entities represent the way in which we want our Data to be designed . They allow the storing of the logical Pieces such as the User with Achievements, the Game while it’s running, the Story, etc. via the JPA Repositories into tables.
By coupling these to the services we can set the fields as intended and needed for Users, Game, Stats, etc. and then by using the DTOs these objects can be communicated with client.

One Entity example:
[`Game.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Game.java)

### Component 3: Game and Story logic
Within the GameService triggered by the GameController we handle the internal logic for processing the game as intended - i.e. creating Game instances, adding player inputs based on turns, handling different voting situations etc.
Within this Service we additionally set the parameters as needed for the Story that after the game is over is saved to the DB. Also the handling of User Stats and Achievements which is triggered in here.


[`GameService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameService.java)

### Component 4: Statistics and Achievements logic
Within the StatsAchvsService we process the Game Results and save them in the DB just like the Story. The Achievements are not just personal, but also include Global features such as whether you are the absolute GenreMaster or in the top percentile.
This logic is triggered by the GameService and GameController in all cases in which a game can end.

[`StatsAchvsService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/StatsAchvsService.java)

### Component 5: User logic
Within the UserService we handle everything that has to do with the User instances, including registering, login, logout, changing password or bio and deleting your account. For account deletion it is important that we also update the other tables in the DB in order to keep the stored data consistent.

[`UserService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserService.java)

---

## 4. Launch & Deployment

## Building with Gradle
You can use the local Gradle Wrapper to build the application.
-   macOS: `./gradlew`
-   Linux: `./gradlew`
-   Windows: `./gradlew.bat`

More Information about [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) and [Gradle](https://gradle.org/docs/).

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew bootRun
```

You can verify that the server is running by visiting `localhost:8080` in your browser.

### Test

```bash
./gradlew test
```

### Development Mode
You can start the backend in development mode, this will automatically trigger a new build and reload the application
once the content of a file has been changed.

Start two terminal windows and run:

`./gradlew build --continuous`

and in the other one:

`./gradlew bootRun`

If you want to avoid running all tests with every change, use the following command instead:

`./gradlew build --continuous -xtest`

## API Endpoint Testing with Postman
We recommend using [Postman](https://www.getpostman.com) to test your API Endpoints.

## Debugging
If something is not working and/or you don't know what is going on. We recommend using a debugger and step-through the process step-by-step.

To configure a debugger for SpringBoot's Tomcat servlet (i.e. the process you start with `./gradlew bootRun` command), do the following:

1. Open Tab: **Run**/Edit Configurations
2. Add a new Remote Configuration and name it properly
3. Start the Server in Debug mode: `./gradlew bootRun --debug-jvm`
4. Press `Shift + F9` or the use **Run**/Debug "Name of your task"
5. Set breakpoints in the application where you need it
6. Step through the process one step at a time

## Testing Code Coverage

`./gradlew clean test jacocoTestReport`

## Docker

### Pull
Ensure that [Docker](https://www.docker.com/) is installed on the machine you wish to run the container.\
First, pull (download) the image with the following command:

```bash
docker pull sopragroup14/sopra-fs26-group-14-server
```

### Run

Then, run the image in a container with the following command:

```bash
docker run -p 8080:8080 sopragroup14/sopra-fs26-group-14-server
```

---

## 5. Roadmap

### Feature 1: Multiple Judges
There is already some effort towards handling multiple judges. However, the game flow design behind it is not fleshed out which leaves this features as not done yet and in the need of correction and advancement.

### Feature 2: Interactive effect cards
The addition of a pool of cards which can be used by the writers and maybe even the judge. The effects could be for example that the opponent is forced to use three words with length being at least 16, or you can give yourself extra time, etc.

### Feature 3: Judge Quote
The judge can also incorporate quotes or use the option of fetching more/less fitting random quotes.

---

## 6. Authors and Acknowledgment
* [@AntoGrgic49](https://github.com/AntoGrgic49)

* [@MaricBoris](https://github.com/MaricBoris)

* [@elvivbert](https://github.com/elvivbert)

* [@Monato11](https://github.com/Monato11)

* [@thomashonzi](https://github.com/thomashonzi)

---

## 7. License
This project is licensed under the [GNU General Public License v3](LICENSE).

---
