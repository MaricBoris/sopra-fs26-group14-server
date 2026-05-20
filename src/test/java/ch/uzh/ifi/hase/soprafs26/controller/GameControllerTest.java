package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.game.GameInputDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.GameStreamService;
import ch.uzh.ifi.hase.soprafs26.service.StatsAchvsService;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tools.jackson.core.JacksonException;
import ch.uzh.ifi.hase.soprafs26.constant.GamePhase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

@WebMvcTest(GameController.class)
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private GameStreamService gameStreamService;

    @MockitoBean
    private StatsAchvsService statsAchvsService;

    @Test
    public void vote_validInput_200Ok() throws Exception {
        Game game = new Game();
        game.setId(1L);
        Story story = new Story();
        game.setStory(story);

        Writer writer = new Writer();
        writer.setId(10L);

        //Set up all function to behave as valid input
        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(writer);
        given(gameService.findUserFromToken(anyString())).willReturn(new User());
        given(gameService.getJudgeFromUser(any(), any())).willReturn(new Judge());
        doNothing().when(gameService).checkGameIsOver(any());
        doNothing().when(gameService).addVote(any(), any(), any());
        given(gameService.allJudgesVoted(any())).willReturn(true);
        given(gameService.determineWinner(any())).willReturn(writer);
        given(gameService.updateStory(any(), any())).willReturn(new Story());
        doNothing().when(gameService).clearVotes(any());
        doNothing().when(gameService).cleanupGame(any());


        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isOk());
        verify(gameStreamService).sendGameToAllClients(any(Game.class));
    }

    @Test
    public void vote_invalidToken_401Unauthorized() throws Exception {
        Game game = new Game();
        game.setId(1L);

        Writer writer = new Writer();
        writer.setId(10L);

        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(writer);
        given(gameService.findUserFromToken(anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error: You are not Authorized."));

        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer invalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void vote_userNotJudge_403Forbidden() throws Exception {
        Game game = new Game();
        game.setId(1L);

        Writer writer = new Writer();
        writer.setId(10L);

        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(writer);
        given(gameService.findUserFromToken(anyString())).willReturn(new User());
        given(gameService.getJudgeFromUser(any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Error: You are not allowed to vote for this game."));

        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isForbidden());
    }

    @Test
    public void vote_gameNotFound_404NotFound() throws Exception {
        given(gameService.getGame(anyLong()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: The provided id is invalid."));

        MockHttpServletRequestBuilder postRequest = post("/games/99/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isNotFound());
    }

    @Test
    public void vote_gameNotOver_400BadRequest() throws Exception {
        Game game = new Game();
        game.setId(1L);

        Writer writer = new Writer();
        writer.setId(10L);

        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(writer);
        given(gameService.findUserFromToken(anyString())).willReturn(new User());
        given(gameService.getJudgeFromUser(any(), any())).willReturn(new Judge());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error: The game is not over."))
                .when(gameService).checkGameIsOver(any());

        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isBadRequest());
    }

    //helper method setup

        private Game createTestGame() {
                Game game = new Game();
                game.setId(1L);
                game.setTimer(60L);
                game.setTurnStartedAt(System.currentTimeMillis());
                game.setCurrentRound(1);
                game.setPhase(GamePhase.WRITING);

                // User 1
                User user1 = new User();
                user1.setId(10L);
                user1.setUsername("charles");
                user1.setToken("token-writer1");
                user1.setBio("Married to my montbretias");
                user1.setPassword("gardenlover");
                user1.setCreationDate(new Date());

                // User 2
                User user2 = new User();
                user2.setId(11L);
                user2.setUsername("lottie");
                user2.setToken("token-writer2");
                user2.setBio("Hugh Grants biggest fan");
                user2.setPassword("TwoWeeksNotice");
                user2.setCreationDate(new Date());

                // User 3
                User user3 = new User();
                user3.setId(12L);
                user3.setUsername("AntonEgo");
                user3.setToken("token-judge1");
                user3.setBio("I will most likely despise your writing");
                user3.setPassword("ratatouille");
                user3.setCreationDate(new Date());

                // Writer 1
                Writer w1 = new Writer();
                w1.setId(100L);
                w1.setUser(user1);
                w1.setTurn(true);
                w1.setGenre("Fantasy");
                w1.setText("Once upon a time there was a grumpy green dragon.");
                w1.setLastSeenAt(System.currentTimeMillis());
                w1.setQuote("A mysterious quote");

                // Writer 2
                Writer w2 = new Writer();
                w2.setId(101L);
                w2.setUser(user2);
                w2.setTurn(false);
                w2.setGenre("Sci-Fi");
                w2.setText("In a distant galaxy");
                w2.setLastSeenAt(System.currentTimeMillis());
                w2.setQuote("No pain no gain");

                List<Writer> writers = new ArrayList<>();
                writers.add(w1);
                writers.add(w2);
                game.setWriters(writers);

                // Judge
                Judge j1 = new Judge();
                j1.setId(200L);
                j1.setUser(user3);
                j1.setInsertions(1L);
                j1.setLastSeenAt(System.currentTimeMillis());

                List<Judge> judges = new ArrayList<>();
                judges.add(j1);
                game.setJudges(judges);

                // Story
                Story story = new Story();
                story.setId(300L);
                story.setWinner(user1);
                story.setLoser(user2);
                story.addContribution(1L, "Amazing story text.");
                story.setHasWinner(true);
                story.setWinGenre("Fantasy");
                story.setLoseGenre("Sci-Fi");
                story.setJudges(new ArrayList<>(List.of(user3)));
                story.setCreationDate(new Date());

                game.setStory(story);

                return game;
        }

    //get game tests

     @Test
    public void getGame_validInput_200Ok() throws Exception {

        Game game = createTestGame();

        given(gameService.getGame(anyLong(), anyString())).willReturn(game);

        MockHttpServletRequestBuilder getRequest = get("/games/1")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.gameId", is(1)))
                .andExpect(jsonPath("$.timer", is(60)))
                .andExpect(jsonPath("$.currentRound", is(1)))
                .andExpect(jsonPath("$.turnStartedAt").exists())
                .andExpect(jsonPath("$.phase", is("WRITING")))

                .andExpect(jsonPath("$.writers").exists())
                .andExpect(jsonPath("$.writers.length()").value(2))

                .andExpect(jsonPath("$.writers[0].id", is(10)))
                .andExpect(jsonPath("$.writers[0].username", is("charles")))
                .andExpect(jsonPath("$.writers[0].turn", is(true)))
                .andExpect(jsonPath("$.writers[0].genre", is("Fantasy")))
                .andExpect(jsonPath("$.writers[0].text", is("Once upon a time there was a grumpy green dragon.")))
                .andExpect(jsonPath("$.writers[0].quote", is("A mysterious quote")))

                .andExpect(jsonPath("$.writers[1].id", is(11)))
                .andExpect(jsonPath("$.writers[1].username", is("lottie")))
                .andExpect(jsonPath("$.writers[1].turn", is(false)))
                .andExpect(jsonPath("$.writers[1].genre", is("Sci-Fi")))
                .andExpect(jsonPath("$.writers[1].text", is("In a distant galaxy")))
                .andExpect(jsonPath("$.writers[1].quote", is("No pain no gain")))

                .andExpect(jsonPath("$.judges").exists())
                .andExpect(jsonPath("$.judges.length()").value(1))
                .andExpect(jsonPath("$.judges[0].id", is(12)))
                .andExpect(jsonPath("$.judges[0].username", is("AntonEgo")))
                .andExpect(jsonPath("$.judges[0].insertions", is(1)))

                .andExpect(jsonPath("$.story").exists())
                .andExpect(jsonPath("$.story.storyContributions").exists())
                .andExpect(jsonPath("$.story.storyContributions[0].text", is("Amazing story text.")))
                .andExpect(jsonPath("$.story.hasWinner", is(true)))
                .andExpect(jsonPath("$.story.winGenre", is("Fantasy")))
                .andExpect(jsonPath("$.story.loseGenre", is("Sci-Fi")))
                .andExpect(jsonPath("$.story.winnerUsername", is("charles")))
                .andExpect(jsonPath("$.story.loserUsername", is("lottie")))

                .andExpect(jsonPath("$.writers[0].user").doesNotExist())
                .andExpect(jsonPath("$.judges[0].user").doesNotExist());

    }

    @Test
    public void getGame_wrongState_400BadRequest() throws Exception {
        given(gameService.getGame(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erroneous Game State"));

        MockHttpServletRequestBuilder getRequest = get("/games/1")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getGame_invalidToken_401Unauthorized() throws Exception {
        given(gameService.getGame(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));

        MockHttpServletRequestBuilder getRequest = get("/games/1")
                .header("Authorization", "Bearer wrong-token")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void getGame_userNotPartOfGame_403Forbidden() throws Exception {
        given(gameService.getGame(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "User not part of game"));

        MockHttpServletRequestBuilder getRequest = get("/games/1")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isForbidden());
    }

    @Test
    public void getGame_gameNotFound_404NotFound() throws Exception {
        given(gameService.getGame(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: A game with that id could not be found"));

        MockHttpServletRequestBuilder getRequest = get("/games/999")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isNotFound());
    }

    
    // POST /games/{gameid}/input

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void saveWriterInput_validInput_200Ok() throws Exception {
        Game game = createTestGame();

        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setPlayer(1);
        inputDTO.setInput("I love cats");

        given(gameService.insertWriterInput(anyLong(), any(), anyString(), anyString())).willReturn(game);

        MockHttpServletRequestBuilder postRequest = post("/games/1/input")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.gameId", is(1)))
                .andExpect(jsonPath("$.timer", is(60)))
                .andExpect(jsonPath("$.currentRound", is(1)))
                .andExpect(jsonPath("$.turnStartedAt").exists())
                .andExpect(jsonPath("$.phase", is("WRITING")))

                .andExpect(jsonPath("$.writers").exists())
                .andExpect(jsonPath("$.writers.length()").value(2))

                .andExpect(jsonPath("$.writers[0].id", is(10)))
                .andExpect(jsonPath("$.writers[0].username", is("charles")))
                .andExpect(jsonPath("$.writers[0].turn", is(true)))
                .andExpect(jsonPath("$.writers[0].genre", is("Fantasy")))
                .andExpect(jsonPath("$.writers[0].text", is("Once upon a time there was a grumpy green dragon.")))
                .andExpect(jsonPath("$.writers[0].quote", is("A mysterious quote")))

                .andExpect(jsonPath("$.writers[1].id", is(11)))
                .andExpect(jsonPath("$.writers[1].username", is("lottie")))
                .andExpect(jsonPath("$.writers[1].turn", is(false)))
                .andExpect(jsonPath("$.writers[1].genre", is("Sci-Fi")))
                .andExpect(jsonPath("$.writers[1].text", is("In a distant galaxy")))
                .andExpect(jsonPath("$.writers[1].quote", is("No pain no gain")))

                .andExpect(jsonPath("$.judges").exists())
                .andExpect(jsonPath("$.judges.length()").value(1))
                .andExpect(jsonPath("$.judges[0].id", is(12)))
                .andExpect(jsonPath("$.judges[0].username", is("AntonEgo")))
                .andExpect(jsonPath("$.judges[0].insertions", is(1)))

                .andExpect(jsonPath("$.story").exists())
                .andExpect(jsonPath("$.story.storyContributions").exists())
                .andExpect(jsonPath("$.story.storyContributions[0].text", is("Amazing story text.")))
                .andExpect(jsonPath("$.story.hasWinner", is(true)))
                .andExpect(jsonPath("$.story.winGenre", is("Fantasy")))
                .andExpect(jsonPath("$.story.loseGenre", is("Sci-Fi")))
                .andExpect(jsonPath("$.story.winnerUsername", is("charles")))
                .andExpect(jsonPath("$.story.loserUsername", is("lottie")))

                .andExpect(jsonPath("$.writers[0].user").doesNotExist())
                .andExpect(jsonPath("$.judges[0].user").doesNotExist());
        verify(gameStreamService).sendGameToAllClients(any(Game.class));
    }

    @Test
    public void saveWriterInput_invalidToken_401Unauthorized() throws Exception {
        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setPlayer(1);
        inputDTO.setInput("Once");

        given(gameService.insertWriterInput(anyLong(), any(), anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));

        MockHttpServletRequestBuilder postRequest = post("/games/1/input")
                .header("Authorization", "Bearer wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isUnauthorized());
        
    }

    @Test
    public void saveWriterInput_notAllowed_403Forbidden() throws Exception {
        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setPlayer(1);
        inputDTO.setInput("Once");

        given(gameService.insertWriterInput(anyLong(), any(), anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "It's not this writers turn!"));

        MockHttpServletRequestBuilder postRequest = post("/games/1/input")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isForbidden());
    }

    @Test
    public void saveWriterInput_gameNotFound_404NotFound() throws Exception {
        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setPlayer(1);
        inputDTO.setInput("Once");

        given(gameService.insertWriterInput(anyLong(), any(), anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: A game with that id could not be found"));

        MockHttpServletRequestBuilder postRequest = post("/games/999/input")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isNotFound());
    }

    @Test
    public void saveWriterInput_wrongState_409Conflict() throws Exception {
        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setPlayer(1);
        inputDTO.setInput("Once");

        given(gameService.insertWriterInput(anyLong(), any(), anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Users may not write anymore"));

        MockHttpServletRequestBuilder postRequest = post("/games/1/input")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isConflict());
    }

  
    // POST /games/{gameid}/draft
   

        @Test
        public void saveWriterDraft_validInput_200Ok() throws Exception {
                Game game = createTestGame();
                game.getWriters().get(0).setText("Draft text");

                GameInputDTO inputDTO = new GameInputDTO();
                inputDTO.setPlayer(1);
                inputDTO.setInput("Draft text");

                given(gameService.saveWriterDraft(anyLong(), anyString(), anyString())).willReturn(game);

                MockHttpServletRequestBuilder postRequest = post("/games/1/draft")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(inputDTO));

                mockMvc.perform(postRequest)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.gameId", is(1)))
                        .andExpect(jsonPath("$.writers").exists())
                        .andExpect(jsonPath("$.writers.length()").value(2))
                        .andExpect(jsonPath("$.writers[0].text", is("Draft text")))
                        .andExpect(jsonPath("$.writers[1].text", is("In a distant galaxy")));
        }

    @Test
    public void saveWriterDraft_gameNotFound_404NotFound() throws Exception {
        GameInputDTO inputDTO = new GameInputDTO();
        inputDTO.setInput("Draft text");

        given(gameService.saveWriterDraft(anyLong(), anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: A game with that id could not be found"));

        MockHttpServletRequestBuilder postRequest = post("/games/999/draft")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(inputDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isNotFound());
    }

    
    // POST /games/{gameid}/leave


    @Test
    public void exitGame_validInput_200Ok() throws Exception {
        doNothing().when(gameService).exitGame(anyLong(), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/leave")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(postRequest)
                .andExpect(status().isOk());
        
    }

    @Test
    public void exitGame_invalidToken_401Unauthorized() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
                .when(gameService).exitGame(anyLong(), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/leave")
                .header("Authorization", "Bearer wrong-token")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(postRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void exitGame_userNotPartOfGame_403Forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "User not part of game"))
                .when(gameService).exitGame(anyLong(), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/leave")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(postRequest)
                .andExpect(status().isForbidden());
    }

    @Test
    public void exitGame_gameNotFound_404NotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: A game with that id could not be found"))
                .when(gameService).exitGame(anyLong(), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/999/leave")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(postRequest)
                .andExpect(status().isNotFound());
    }
@Test
public void streamGame_validInput_200Ok() throws Exception {
    Game game = createTestGame();

    given(gameService.getGame(anyLong(), anyString())).willReturn(game);
    given(gameStreamService.addClient(anyLong())).willReturn(new SseEmitter(0L));

    MockHttpServletRequestBuilder getRequest = get("/games/1/stream")
            .param("token", "token123");

    mockMvc.perform(getRequest)
            .andExpect(status().isOk());

    verify(gameService).getGame(anyLong(), anyString());
    verify(gameStreamService).addClient(anyLong());
}


    @Test
    public void vote_winnerAlreadyExists_returnsImmediately() throws Exception {
        Game game = new Game();
        Story story = new Story();
        User existingWinner = new User();
        story.setWinner(existingWinner);  
        game.setStory(story);
        
        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(new Writer());
        given(gameService.findUserFromToken(anyString())).willReturn(new User());
        given(gameService.getJudgeFromUser(any(), any())).willReturn(new Judge());
        doNothing().when(gameService).checkGameIsOver(any());
        doNothing().when(gameService).addVote(any(), any(), any());

        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isOk());
        
        verify(gameService, never()).determineWinner(any());
        verify(gameService, never()).updateStory(any(), any());
    }

    @Test
    public void vote_waitingForOtherJudges_waitsAndReturns200() throws Exception {
        Game game = new Game();
        game.setId(1L);
        Story story = new Story();
        game.setStory(story);

        Writer writer = new Writer();
        writer.setId(10L);

        given(gameService.getGame(anyLong())).willReturn(game);
        given(gameService.findWriterFromId(anyLong(), any(Game.class))).willReturn(writer);
        given(gameService.findUserFromToken(anyString())).willReturn(new User());
        given(gameService.getJudgeFromUser(any(), any())).willReturn(new Judge());
        doNothing().when(gameService).checkGameIsOver(any());
        doNothing().when(gameService).addVote(any(), any(), any());

        given(gameService.allJudgesVoted(any()))
                .willReturn(false, false, true);  // Returns false twice, then true

        given(gameService.determineWinner(any())).willReturn(writer);
        given(gameService.updateStory(any(), any())).willReturn(new Story());
        doNothing().when(gameService).clearVotes(any());
        doNothing().when(gameService).cleanupGame(any());

        MockHttpServletRequestBuilder postRequest = post("/games/1/vote")
                .header("Authorization", "Bearer token123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("10");

        mockMvc.perform(postRequest)
                .andExpect(status().isOk());
    }

    // --- DELETE /games/{gameId} ---

    @Test
    public void deleteGame_validInput_200Ok() throws Exception {
        Game game = new Game();
        game.setId(1L);

        given(gameService.getGame(anyLong())).willReturn(game);
        doNothing().when(gameService).deleteGame(any(Game.class));
        doNothing().when(gameStreamService).sendGameDeletedToAllClients(anyLong());

        MockHttpServletRequestBuilder deleteRequest = delete("/games/1")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(deleteRequest)
                .andExpect(status().isOk());

        verify(gameService).deleteGame(game);
        verify(gameStreamService).sendGameDeletedToAllClients(1L);
    }

    // --- GET /games/current ---

    @Test
    public void getCurrentGame_validRequest_200Ok() throws Exception {
        Game game = createTestGame();

        given(gameService.getGameForUser(anyString())).willReturn(game);

        MockHttpServletRequestBuilder getRequest = get("/games/current")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", is(1)))
                .andExpect(jsonPath("$.phase", is("WRITING")));
    }

    @Test
    public void getCurrentGame_noActiveGame_404NotFound() throws Exception {
        given(gameService.getGameForUser(anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No active game found for user"));

        MockHttpServletRequestBuilder getRequest = get("/games/current")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(getRequest)
                .andExpect(status().isNotFound());
    }

    // --- GET /games/{gameid}/quotes ---

    @Test
    public void fetchQuote_validRequest_200Ok() throws Exception {
        Game game = createTestGame();

        given(gameService.assignQuote(anyLong(), any(Integer.class), anyString())).willReturn(game);

        MockHttpServletRequestBuilder getRequest = get("/games/1/quotes")
                .param("player", "1")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", is(1)));

        verify(gameStreamService).sendGameToAllClients(any(Game.class));
    }

    // --- POST /games/{gameId}/reduce-time ---

    @Test
    public void reduceTime_validRequest_200Ok() throws Exception {
        Game game = createTestGame();
        game.setTimer(30L); // Simulating reduced time

        given(gameService.reduceTime(anyLong(), anyString())).willReturn(game);

        MockHttpServletRequestBuilder postRequest = post("/games/1/reduce-time")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timer", is(30)));
    }

    @Test
    public void reduceTime_notAuthorized_403Forbidden() throws Exception {
        given(gameService.reduceTime(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only judges can reduce time"));

        MockHttpServletRequestBuilder postRequest = post("/games/1/reduce-time")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isForbidden());
    }

    // --- POST /games/{gameId}/force-judge-vote ---

    @Test
    public void forceJudgeVote_validInput_200Ok() throws Exception {
        Game game = new Game();
        game.setId(1L);

        given(gameService.getGame(anyLong())).willReturn(game);
        doNothing().when(gameService).forceJudgeAutoVote(any(Game.class), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/force-judge-vote")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isOk());

        verify(gameService).forceJudgeAutoVote(game, "Bearer token123");
    }

    @Test
    public void forceJudgeVote_gameNotFound_404NotFound() throws Exception {
        given(gameService.getGame(anyLong()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Error: The provided id is invalid."));

        MockHttpServletRequestBuilder postRequest = post("/games/99/force-judge-vote")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isNotFound());
    }

    @Test
    public void forceJudgeVote_invalidToken_401Unauthorized() throws Exception {
        Game game = new Game();
        game.setId(1L);

        given(gameService.getGame(anyLong())).willReturn(game);
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
                .when(gameService).forceJudgeAutoVote(any(Game.class), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/force-judge-vote")
                .header("Authorization", "Bearer wrong-token");

        mockMvc.perform(postRequest)
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void forceJudgeVote_notWriter_403Forbidden() throws Exception {
        Game game = new Game();
        game.setId(1L);

        given(gameService.getGame(anyLong())).willReturn(game);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only writers can force a judge auto-vote."))
                .when(gameService).forceJudgeAutoVote(any(Game.class), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/force-judge-vote")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isForbidden());
    }

    @Test
    public void forceJudgeVote_timerNotExpired_409Conflict() throws Exception {
        Game game = new Game();
        game.setId(1L);

        given(gameService.getGame(anyLong())).willReturn(game);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Judge timer has not expired yet."))
                .when(gameService).forceJudgeAutoVote(any(Game.class), anyString());

        MockHttpServletRequestBuilder postRequest = post("/games/1/force-judge-vote")
                .header("Authorization", "Bearer token123");

        mockMvc.perform(postRequest)
                .andExpect(status().isConflict());
    }
}