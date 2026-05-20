package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AchievementType;
import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class StatsAchvsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private GenreMasterRepository genreMasterRepository;

    @InjectMocks
    private StatsAchvsService statsAchvsService;

    private User winner;
    private User loser;
    private User judge1;
    private User judge2;
    private Game game;
    private Story story;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup Users
        winner = createUser(1L, "winnerUser");
        loser = createUser(2L, "loserUser");
        judge1 = createUser(3L, "judgeOne");

        // Setup Story
        story = new Story();
        story.setWinner(winner);
        story.setLoser(loser);
        story.setWinGenre("Horror");
        story.setJudges(Collections.singletonList(judge1));
        story.setHasWinner(true);

        // Setup Game
        game = new Game();
        game.setId(100L);
        game.setStory(story);

        // Auto-mock achievements so we don't have to define them one-by-one everywhere
        when(achievementRepository.findByName(anyString())).thenAnswer(invocation -> {
            Achievement ach = new Achievement();
            ach.setName(invocation.getArgument(0));
            return ach;
        });

        // Default mock for user repository count (prevents TopPercentile logic from crashing or granting unexpectedly)
        when(userRepository.count()).thenReturn(5L);

        when(genreMasterRepository.save(any(GenreMaster.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User createUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setStatistics(new UserStatistics());
        u.setAchievements(new ArrayList<>());
        return u;
    }

    // --- Core Stats & Judges Tests ---

    @Test
    public void processGameResults_normalGame_updatesStatsCorrectly() {
        statsAchvsService.processGameResults(game, false);

        // Verify Winner Stats
        assertEquals(1, winner.getStatistics().getGamesWon());
        assertEquals(1, winner.getStatistics().getGamesPlayed());
        assertEquals(1, winner.getStatistics().getWinsByGenre().get("Horror"));
        assertEquals(0, winner.getStatistics().getSuddenDeathWins());
        assertEquals(0, winner.getStatistics().getUnanimousWins());

        // Verify Loser Stats
        assertEquals(0, loser.getStatistics().getGamesWon());
        assertEquals(1, loser.getStatistics().getGamesPlayed());
        assertEquals(1, loser.getStatistics().getGamesLost());

        // Verify Judges Stats
        assertEquals(1, judge1.getStatistics().getTotalVotesCast());

        // Verifies the save calls were triggered
        verify(userRepository).save(winner);
        verify(userRepository).save(loser);
        verify(userRepository).saveAll(anyList());
    }

    @Test
    public void processGameResults_suddenDeathAndUnanimous_updatesStatsCorrectly() {
        story.setTieBreakerQuote("A dark and stormy night."); // Triggers isSuddenDeath = true

        statsAchvsService.processGameResults(game, true); // Triggers isUnanimous = true

        assertEquals(1, winner.getStatistics().getSuddenDeathWins());
        assertEquals(1, winner.getStatistics().getUnanimousWins());

        // Verify achievements for these specific feats were granted
        assertTrue(hasAchievement(winner, AchievementType.SUDDEN_DEATH_SURVIVOR));
        assertTrue(hasAchievement(winner, AchievementType.CROWD_FAVORITE));
    }

    // --- Achievement Threshold Tests ---

    @Test
    public void processGameResults_grantsRookieScribeOnFirstGame() {
        statsAchvsService.processGameResults(game, false);

        assertTrue(hasAchievement(winner, AchievementType.ROOKIE_SCRIBE));
        assertTrue(hasAchievement(loser, AchievementType.ROOKIE_SCRIBE));
    }

    @Test
    public void processGameResults_doesNotGrantDuplicateAchievements() {
        // Pre-give the Rookie Scribe achievement to the winner
        Achievement rookie = new Achievement();
        rookie.setName(AchievementType.ROOKIE_SCRIBE.name());
        UserAchievement ua = new UserAchievement();
        ua.setAchievement(rookie);
        winner.getAchievements().add(ua);

        statsAchvsService.processGameResults(game, false);

        // Ensure it didn't add a second copy
        long rookieCount = winner.getAchievements().stream()
                .filter(a -> a.getAchievement().getName().equals("ROOKIE_SCRIBE"))
                .count();
        assertEquals(1, rookieCount);
    }

    @Test
    public void processGameResults_grantsHighTierAchievements() {
        // Pre-load stats to 1 less than the threshold
        winner.getStatistics().setGamesWon(19);
        winner.getStatistics().getWinsByGenre().put("Horror", 9);

        statsAchvsService.processGameResults(game, false);

        // Win pushes them to 20 and 10
        assertTrue(hasAchievement(winner, AchievementType.PUBLISHED_AUTHOR));
        assertTrue(hasAchievement(winner, AchievementType.MASTER_OF_MACABRE));
        assertFalse(hasAchievement(judge1, AchievementType.ROOKIE_SCRIBE), "Judge should NOT get Rookie Scribe");
    }

    @Test
    public void processGameResults_achievementNullInDb_handlesGracefully() {
        // Mock DB returning null for an achievement
        when(achievementRepository.findByName(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> statsAchvsService.processGameResults(game, false));

        // Ensure no achievements were added because they didn't exist in DB
        assertTrue(winner.getAchievements().isEmpty());
    }

    // --- Top Percentile (Legend) Tests ---

    @Test
    public void checkTopPercentile_lessThan10Users_doesNothing() {
        when(userRepository.count()).thenReturn(9L);
        winner.getStatistics().getWinsByGenre().put("Horror", 50);

        statsAchvsService.processGameResults(game, false);

        assertFalse(hasAchievement(winner, AchievementType.HORROR_LEGEND));
    }

    @Test
    public void checkTopPercentile_top1Percent_grantsLegend() {
        when(userRepository.count()).thenReturn(100L);
        winner.getStatistics().getWinsByGenre().put("Horror", 50);

        // Mock userRepository.findAll() returning 100 dummy users, none with more than 50 wins
        when(userRepository.findAll()).thenReturn(Arrays.asList(winner, loser));

        statsAchvsService.processGameResults(game, false);

        assertTrue(hasAchievement(winner, AchievementType.HORROR_LEGEND));
    }

    @Test
    public void checkTopPercentile_notTop1Percent_noLegend() {
        when(userRepository.count()).thenReturn(100L);
        winner.getStatistics().getWinsByGenre().put("Horror", 10);

        // Create 2 users better than the winner (2/100 = 2% > 1%)
        User better1 = createUser(10L, "b1");
        better1.getStatistics().getWinsByGenre().put("Horror", 20);
        User better2 = createUser(11L, "b2");
        better2.getStatistics().getWinsByGenre().put("Horror", 20);

        when(userRepository.findAll()).thenReturn(Arrays.asList(winner, better1, better2));

        statsAchvsService.processGameResults(game, false);

        assertFalse(hasAchievement(winner, AchievementType.HORROR_LEGEND));
    }

    @Test
    public void checkTopPercentile_zeroWins_noLegend() {
        when(userRepository.count()).thenReturn(100L);
        // Even if 0 people are better (0/100 = 0%), 0 wins should not grant Legend
        story.setWinGenre("Comedy"); // Ensure Horror doesn't increment

        when(userRepository.findAll()).thenReturn(Arrays.asList(winner));

        statsAchvsService.processGameResults(game, false);

        assertFalse(hasAchievement(winner, AchievementType.HORROR_LEGEND));
    }

    // --- Genre Master Logic Tests ---

    @Test
    public void setGenreMaster_gmDoesNotExist_createsNew() {
        when(genreMasterRepository.findByGenre("Horror")).thenReturn(null);
        when(genreMasterRepository.save(any(GenreMaster.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(winner));

        statsAchvsService.processGameResults(game, false);

        verify(genreMasterRepository, atLeastOnce()).save(any(GenreMaster.class));
    }

    @Test
    public void setGenreMaster_gmExists_winnerTakesThrone() {
        // Existing GenreMaster is 'loser', with 1 point
        GenreMaster gm = new GenreMaster();
        gm.setGenre("Horror");
        gm.setCurrentMaster(loser);
        Map<Long, Integer> votes = new HashMap<>();
        votes.put(loser.getId(), 1);
        gm.setVotes(votes);

        when(genreMasterRepository.findByGenre("Horror")).thenReturn(gm);
        when(userRepository.findById(1L)).thenReturn(Optional.of(winner)); // ID 1 is winner

        // Execute: Winner will get 2 points (2 judges in the story), surpassing Loser's 1 point
        statsAchvsService.processGameResults(game, false);

        // Verify winner took the throne
        assertEquals(winner.getId(), gm.getCurrentMaster().getId());
        assertEquals(1, gm.getVotes().get(winner.getId()));
    }

    @Test
    public void setGenreMaster_gmExists_winnerRemainsMaster() {
        // Existing GenreMaster is ALREADY the winner
        GenreMaster gm = new GenreMaster();
        gm.setGenre("Horror");
        gm.setCurrentMaster(winner);
        Map<Long, Integer> votes = new HashMap<>();
        votes.put(winner.getId(), 10);
        gm.setVotes(votes);

        when(genreMasterRepository.findByGenre("Horror")).thenReturn(gm);
        // Do not mock userRepository.findById to ensure it ISN'T called
        // (branch: !gm.getCurrentMaster().getId().equals(topCandidateId) evaluates to false)

        statsAchvsService.processGameResults(game, false);

        // Verify winner stayed master, points increased by 2
        assertEquals(winner, gm.getCurrentMaster());
        assertEquals(11, gm.getVotes().get(winner.getId()));
        verify(userRepository, never()).findById(anyLong());
    }

    // --- Helper ---
    private boolean hasAchievement(User u, AchievementType type) {
        return u.getAchievements().stream()
                .anyMatch(a -> a.getAchievement().getName().equals(type.name()));
    }
}