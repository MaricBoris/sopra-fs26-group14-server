package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AchievementType;
import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class StatsAchvsServiceIntegrationTest {

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GenreMasterRepository genreMasterRepository;

    @Autowired
    private StatsAchvsService statsAchvsService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private StoryRepository storyRepository;

    private User winner;
    private User loser;
    private User judge;
    private Game game;

    @BeforeEach
    public void setup() {
        roomRepository.deleteAll();
        gameRepository.deleteAll();
        storyRepository.deleteAll();
        userAchievementRepository.deleteAll();
        genreMasterRepository.deleteAll();
        userRepository.deleteAll();

        roomRepository.flush();
        userRepository.flush();

        getOrCreateAchievement(AchievementType.ROOKIE_SCRIBE.name(), "Rookie Scribe", "Win your first game.", "quill");
        getOrCreateAchievement(AchievementType.SUDDEN_DEATH_SURVIVOR.name(), "Survivor", "Win a sudden death tiebreaker.", "skull");
        getOrCreateAchievement(AchievementType.CROWD_FAVORITE.name(), "Crowd Favorite", "Win with a unanimous vote.", "star");

        winner = createUser("dbWinner");
        loser = createUser("dbLoser");
        judge = createUser("dbJudge");

        Story story = new Story();
        story.setWinner(winner);
        story.setLoser(loser);
        story.setWinGenre("Horror");
        story.setJudges(new ArrayList<>(List.of(judge)));
        story.setHasWinner(true);

        game = new Game();
        game.setId(1L);
        game.setStory(story);
    }

    private void getOrCreateAchievement(String name, String displayName, String description, String icon) {
        Achievement existing = achievementRepository.findByName(name);
        if (existing == null) {
            Achievement ach = new Achievement();
            ach.setName(name);
            ach.setDisplayName(displayName);
            ach.setDescription(description);
            ach.setIcon(icon);
            achievementRepository.saveAndFlush(ach);
        }
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("pass");
        user.setToken("token-" + username);

        UserStatistics stats = new UserStatistics();
        stats.setUser(user);
        user.setStatistics(stats);

        user.setAchievements(new ArrayList<>());
        return userRepository.save(user);
    }

    // --- Integration Tests ---

    @Test
    public void processGameResults_normalGame_persistsStatsAndRookieAchievement() {
        statsAchvsService.processGameResults(game, false);

        User dbWinner = userRepository.findById(winner.getId()).orElseThrow();
        User dbLoser = userRepository.findById(loser.getId()).orElseThrow();
        User dbJudge = userRepository.findById(judge.getId()).orElseThrow();

        assertEquals(1, dbWinner.getStatistics().getGamesWon());
        assertEquals(1, dbWinner.getStatistics().getWinsByGenre().get("Horror"));
        assertEquals(1, dbLoser.getStatistics().getGamesLost());
        assertEquals(1, dbJudge.getStatistics().getTotalVotesCast());

        boolean winnerHasRookie = dbWinner.getAchievements().stream()
                .anyMatch(a -> a.getAchievement().getName().equals("ROOKIE_SCRIBE"));
        assertTrue(winnerHasRookie, "Winner should have Rookie Scribe saved in DB");

        GenreMaster gm = genreMasterRepository.findByGenre("Horror");
        assertNotNull(gm);
        assertEquals(dbWinner.getId(), gm.getCurrentMaster().getId());
        assertEquals(1, gm.getVotes().get(dbWinner.getId())); // 1 point for the 1 judge
    }

    @Test
    public void processGameResults_suddenDeathAndUnanimous_persistsSpecialAchievements() {
        game.getStory().setTieBreakerQuote("It was a dark and stormy night...");

        statsAchvsService.processGameResults(game, true);

        User dbWinner = userRepository.findById(winner.getId()).orElseThrow();

        assertEquals(1, dbWinner.getStatistics().getSuddenDeathWins());
        assertEquals(1, dbWinner.getStatistics().getUnanimousWins());

        boolean hasSuddenDeath = dbWinner.getAchievements().stream()
                .anyMatch(a -> a.getAchievement().getName().equals("SUDDEN_DEATH_SURVIVOR"));
        boolean hasCrowdFav = dbWinner.getAchievements().stream()
                .anyMatch(a -> a.getAchievement().getName().equals("CROWD_FAVORITE"));

        assertTrue(hasSuddenDeath, "Winner should have Sudden Death achievement saved");
        assertTrue(hasCrowdFav, "Winner should have Crowd Favorite achievement saved");
    }

    @Test
    public void setGenreMaster_updatesExistingMasterInDatabase() {
        GenreMaster gm = new GenreMaster();
        gm.setGenre("Horror");
        gm.setCurrentMaster(loser);
        gm.getVotes().put(loser.getId(), 5);
        gm.getVotes().put(winner.getId(), 4);
        genreMasterRepository.save(gm);

        User judge2 = createUser("dbJudge2");
        game.getStory().getJudges().add(judge2);

        statsAchvsService.processGameResults(game, false);

        GenreMaster updatedGm = genreMasterRepository.findByGenre("Horror");

        assertEquals(winner.getId(), updatedGm.getCurrentMaster().getId(), "Winner should usurp the throne");
        assertEquals(6, updatedGm.getVotes().get(winner.getId()), "Winner should now have 6 total points");
    }
}