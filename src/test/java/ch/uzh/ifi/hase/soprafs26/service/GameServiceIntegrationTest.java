package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GamePhase;
import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.RoomRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebAppConfiguration
@SpringBootTest
@Transactional
public class GameServiceIntegrationTest {

    @Qualifier("gameRepository")
    @Autowired
    private GameRepository gameRepository;

    @Qualifier("roomRepository")
    @Autowired
    private RoomRepository roomRepository;

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameService gameService;

    @Autowired
    private UserService userService;

    @Autowired
    private StatsAchvsService statsAchvsService;

    private User user1;
    private User user2;
    private User user3;

    // --- Helpers ---

    private Game setupActiveGame() {
        Game game = new Game();
        game.setPhase(GamePhase.WRITING);
        game.setTimer(60L);
        game.setTurnStartedAt(System.currentTimeMillis() - 10000L); // 10s elapsed
        game.setMaxRounds(10);
        game.setCurrentRound(1);
        game.setRoundResolved(false);

        Judge judge = new Judge(user1);

        Writer w1 = new Writer();
        w1.setUser(user2);
        w1.setTurn(true);
        w1.setReduceTimeReceived(0);

        Writer w2 = new Writer();
        w2.setUser(user3);
        w2.setTurn(false);

        game.setJudges(new ArrayList<>(List.of(judge)));
        game.setWriters(new ArrayList<>(List.of(w1, w2)));

        Story story = new Story();
        story.setStoryContributions(new ArrayList<>());
        game.setStory(story);

        return gameRepository.saveAndFlush(game);
    }

    @BeforeEach
    public void setup() {

        gameRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User();
        user1.setUsername("judgeUser");
        user1.setPassword("password");
        user1 = userService.createUser(user1);

        user2 = new User();
        user2.setUsername("writerUser1");
        user2.setPassword("password");
        user2 = userService.createUser(user2);

        user3 = new User();
        user3.setUsername("writerUser2");
        user3.setPassword("password");
        user3 = userService.createUser(user3);
    }

    // --- getGame ---

    @Test
    public void getGame_validId_returnsGame() {
        Game game = new Game();
        game = gameRepository.save(game);

        Game found = gameService.getGame(game.getId());

        assertNotNull(found);
        assertEquals(game.getId(), found.getId());
    }

    @Test
    public void getGame_invalidId_throws404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.getGame(999L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    // --- findUserFromToken ---

    @Test
    public void findUserFromToken_validToken_returnsUser() {
        User found = gameService.findUserFromToken(user1.getToken());

        assertNotNull(found);
        assertEquals(user1.getId(), found.getId());
    }

    @Test
    public void findUserFromToken_invalidToken_throws401() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.findUserFromToken("fake-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    // --- getJudgeFromUser ---

    @Test
    public void getJudgeFromUser_userIsJudge_returnsJudge() {
        Judge judge = new Judge(user1);

        Game game = new Game();
        game.setJudges(List.of(judge));
        game = gameRepository.save(game);

        Judge found = gameService.getJudgeFromUser(user1, game);

        assertNotNull(found);
        assertEquals(user1.getId(), found.getUser().getId());
    }

    @Test
    public void getJudgeFromUser_userIsNotJudge_throws403() {
        Judge judge = new Judge(user1);

        Game game = new Game();
        game.setJudges(List.of(judge));
        game = gameRepository.save(game);

        final Game savedGame = game;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.getJudgeFromUser(user2, savedGame));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // --- getWriterFromUser ---

    @Test
    public void getWriterFromUser_userIsWriter_returnsWriter() {
        Writer writer = new Writer();
        writer.setUser(user2);

        Game game = new Game();
        game.setWriters(List.of(writer));
        game = gameRepository.save(game);

        Writer found = gameService.getWriterFromUser(user2, game);

        assertNotNull(found);
        assertEquals(user2.getId(), found.getUser().getId());
    }

    @Test
    public void getWriterFromUser_userIsNotWriter_throws400() {
        Writer writer = new Writer();
        writer.setUser(user2);

        Game game = new Game();
        game.setWriters(List.of(writer));
        game = gameRepository.save(game);

        final Game savedGame = game;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.getWriterFromUser(user3, savedGame));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    // --- checkGameIsOver ---

    @Test
    public void checkGameIsOver_timerZero_noException() {
        Game game = new Game();
        game.setTimer(0L);
        game.setPhase(GamePhase.EVALUATION);
        game = gameRepository.save(game);

        final Game savedGame = game;
        assertDoesNotThrow(() -> gameService.checkGameIsOver(savedGame));
    }

    @Test
    public void checkGameIsOver_timerNotZero_throws400() {
        Game game = new Game();
        game.setTimer(60L);
        game = gameRepository.save(game);

        final Game savedGame = game;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.checkGameIsOver(savedGame));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    // --- updateStory ---

    @Test
    public void updateStory_createsAndPersistsStory() {
        Writer winner = new Writer();
        winner.setUser(user2);
        winner.setGenre("Horror");

        Writer loser = new Writer();
        loser.setUser(user3);
        loser.setGenre("Comedy");

        Judge judge = new Judge(user1);

        Story initialStory = new Story(null, null, new ArrayList<>(), false, null, null, new ArrayList<>());


        Game game = new Game();
        game.setWriters(List.of(winner, loser));
        game.setJudges(List.of(judge));
        game.setStory(initialStory);
        game = gameRepository.save(game);

        Story result = gameService.updateStory(winner, game);

        assertNotNull(result);
        assertEquals(user2, result.getWinner());
        assertEquals(user3, result.getLoser());
        assertEquals("Horror", result.getWinGenre());
        assertEquals("Comedy", result.getLoseGenre());
        

        Game reloaded = gameRepository.findById(game.getId()).orElse(null);
        assertNotNull(reloaded);
        assertNotNull(reloaded.getStory().getWinner());
    }

        // --- determineWinner with no votes ---

    @Test
    public void determineWinner_noVotes_returnsNull() {
        Game game = new Game();
        game = gameRepository.save(game);

        Writer winner = gameService.determineWinner(game);
        assertNull(winner);
    }

    // --- cleanupGame ---

    @Test
    public void cleanupGame_deletesGameAndClearsLists() {
        Writer writer = new Writer();
        writer.setUser(user2);

        Judge judge = new Judge(user1);

        Game game = new Game();
        game.setWriters(new ArrayList<>(List.of(writer)));
        game.setJudges(new ArrayList<>(List.of(judge)));
        game = gameRepository.save(game);

        Long gameId = game.getId();

        gameService.cleanupGame(game);

        Game result = gameRepository.findById(gameId).orElseThrow();
        assertEquals(GamePhase.FINISHED, result.getPhase());
    }

    @Test
    public void reduceTime_validJudge_reducesTimeAndPersists() {
        Game game = setupActiveGame();
        Long originalTurnStartedAt = game.getTurnStartedAt();

        Game result = gameService.reduceTime(game.getId(), "Bearer " + user1.getToken());

        assertEquals(1, result.getWriters().get(0).getReduceTimeReceived());
        assertTrue(result.getTurnStartedAt() < originalTurnStartedAt);
    }

    @Test
    public void reduceTime_userNotJudge_throws403() {
        Game game = setupActiveGame();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.reduceTime(game.getId(), "Bearer " + user2.getToken()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    public void reduceTime_wrongPhase_throws409() {
        Game game = setupActiveGame();
        game.setPhase(GamePhase.SUDDEN_DEATH);
        gameRepository.saveAndFlush(game);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.reduceTime(game.getId(), "Bearer " + user1.getToken()));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    // --- getGameForUser (Integration) ---

    @Test
    public void getGameForUser_userInGame_returnsGame() {
        Game game = setupActiveGame();

        Game found = gameService.getGameForUser("Bearer " + user2.getToken());

        assertEquals(game.getId(), found.getId());
    }

    @Test
    public void getGameForUser_userNotInGame_throws404() {
        setupActiveGame();

        User outsider = new User();
        outsider.setUsername("outsider");
        outsider.setPassword("pass");
        outsider = userService.createUser(outsider);

        final String token = "Bearer " + outsider.getToken();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.getGameForUser(token));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    // --- exitGame (Integration) ---

    @Test
    public void exitGame_writerLeaves_deletesGameDueToLackOfPlayers() {
        Game game = setupActiveGame();
        Long gameId = game.getId();

        gameService.exitGame(gameId, "Bearer " + user2.getToken());

        assertTrue(gameRepository.findById(gameId).isEmpty());
    }

    @Test
    public void exitGame_outsiderLeaves_throws403() {
        Game game = setupActiveGame();

        User outsider = new User();
        outsider.setUsername("outsider2");
        outsider.setPassword("pass");
        outsider = userService.createUser(outsider);

        User finalOutsider = outsider;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.exitGame(game.getId(), "Bearer " + finalOutsider.getToken()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // --- saveWriterDraft (Integration) ---

    @Test
    public void saveWriterDraft_validWriterTurn_savesDraft() {
        Game game = setupActiveGame();

        gameService.saveWriterDraft(game.getId(), "My draft text", "Bearer " + user2.getToken());

        Game fromDb = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals("My draft text", fromDb.getWriters().get(0).getText());
    }

    @Test
    public void saveWriterDraft_wrongTurn_throws403() {
        Game game = setupActiveGame();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.saveWriterDraft(game.getId(), "Draft", "Bearer " + user3.getToken()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // --- forceJudgeAutoVote (Integration) ---

    private Game setupEvaluationGame(long timerSeconds, long elapsedMillis) {
        Game game = new Game();
        game.setPhase(GamePhase.EVALUATION);
        game.setTimer(timerSeconds);
        game.setTurnStartedAt(System.currentTimeMillis() - elapsedMillis);
        game.setMaxRounds(10);
        game.setCurrentRound(1);
        game.setRoundResolved(false);

        Judge judge = new Judge(user1);

        Writer w1 = new Writer();
        w1.setUser(user2);
        w1.setTurn(false);

        Writer w2 = new Writer();
        w2.setUser(user3);
        w2.setTurn(false);

        game.setJudges(new ArrayList<>(List.of(judge)));
        game.setWriters(new ArrayList<>(List.of(w1, w2)));

        Story story = new Story();
        story.setStoryContributions(new ArrayList<>());
        game.setStory(story);

        return gameRepository.saveAndFlush(game);
    }

    @Test
    public void forceJudgeAutoVote_validWriter_timerExpired_resolvesRound() {
        // timer=10s, elapsed=20s → timer has expired
        Game game = setupEvaluationGame(10L, 20_000L);

        gameService.forceJudgeAutoVote(game, "Bearer " + user2.getToken());

        assertEquals(GamePhase.FINISHED, game.getPhase());
        assertNotNull(game.getStory().getWinner());
    }

    @Test
    public void forceJudgeAutoVote_notWriter_throws403() {
        Game game = setupEvaluationGame(10L, 20_000L);

        User outsider = new User();
        outsider.setUsername("outsider");
        outsider.setPassword("pass");
        outsider = userService.createUser(outsider);

        User finalOutsider = outsider;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.forceJudgeAutoVote(game, "Bearer " + finalOutsider.getToken()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    public void forceJudgeAutoVote_invalidToken_throws401() {
        Game game = setupEvaluationGame(10L, 20_000L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.forceJudgeAutoVote(game, "Bearer fake-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    public void forceJudgeAutoVote_timerNotExpired_throws409() {
        // timer=60s, elapsed=5s → timer has NOT expired
        Game game = setupEvaluationGame(60L, 5_000L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.forceJudgeAutoVote(game, "Bearer " + user2.getToken()));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void forceJudgeAutoVote_wrongPhase_doesNothing() {
        Game game = setupEvaluationGame(10L, 20_000L);
        game.setPhase(GamePhase.WRITING);
        gameRepository.saveAndFlush(game);

        gameService.forceJudgeAutoVote(game, "Bearer " + user2.getToken());

        assertEquals(GamePhase.WRITING, game.getPhase());
        assertNull(game.getStory().getWinner());
    }

    @Test
    public void forceJudgeAutoVote_winnerAlreadySet_isIdempotent() {
        Game game = setupEvaluationGame(10L, 20_000L);

        gameService.forceJudgeAutoVote(game, "Bearer " + user2.getToken());
        assertEquals(GamePhase.FINISHED, game.getPhase());
        assertNotNull(game.getStory().getWinner());

        // second call must not throw and must leave game unchanged
        gameService.forceJudgeAutoVote(game, "Bearer " + user2.getToken());
        assertEquals(GamePhase.FINISHED, game.getPhase());
        assertNotNull(game.getStory().getWinner());
    }

    // --- insertWriterInput (Integration) ---

    @Test
    public void insertWriterInput_validInput_persistsAndSwitchesTurn() {
        Game game = setupActiveGame();
        int initialRound = game.getCurrentRound();

        gameService.insertWriterInput(game.getId(), 1, "The end.", "Bearer " + user2.getToken());

        Game fromDb = gameRepository.findById(game.getId()).orElseThrow();

        Writer submittingWriter = fromDb.getWriters().stream()
                .filter(w -> w.getUser().getId().equals(user2.getId()))
                .findFirst()
                .orElseThrow();

        Writer otherWriter = fromDb.getWriters().stream()
                .filter(w -> w.getUser().getId().equals(user3.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals("", submittingWriter.getText());

        assertFalse(fromDb.getStory().getStoryContributions().isEmpty());
        assertEquals("The end.", fromDb.getStory().getStoryContributions().get(0).getText());

        assertFalse(submittingWriter.getTurn());
        assertTrue(otherWriter.getTurn());

        assertTrue(fromDb.getCurrentRound() > initialRound);
    }
}