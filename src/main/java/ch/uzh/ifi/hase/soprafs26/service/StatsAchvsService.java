package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AchievementType;
import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class StatsAchvsService {

    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final GenreMasterRepository genreMasterRepository;

    public StatsAchvsService(UserRepository userRepository,
                             AchievementRepository achievementRepository,
                             UserAchievementRepository userAchievementRepository,
                             GenreMasterRepository genreMasterRepository) {
        this.userRepository = userRepository;
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.genreMasterRepository = genreMasterRepository;
    }

    public void processGameResults(Game game, boolean isUnanimous) {
        Story story = game.getStory();
        User winner = story.getWinner();
        User loser = story.getLoser();
        List<User> judges = story.getJudges();
        String genre = story.getWinGenre();

        boolean isSuddenDeath = game.getStory().getTieBreakerQuote() != null;

        setStats(winner, true, genre, isSuddenDeath, isUnanimous);
        setStats(loser, false, genre, isSuddenDeath, false);

        for (User judge : judges) {
            judge.getStatistics().setWinsAsJudge(judge.getStatistics().getWinsAsJudge() + 1);
            judge.getStatistics().setTotalVotesCast(judge.getStatistics().getTotalVotesCast() + 1);
        }

        setUserAchievements(winner);
        setUserAchievements(loser);
        judges.forEach(this::setUserAchievements);

        setGenreMaster(game);

        userRepository.saveAll(judges);
        userRepository.save(winner);
        userRepository.save(loser);
    }

    // stats patch, we make +1 for gamesplayed writers (not judge!) and winsasjudge +1 for judge and rookiescribe for writers
    public void processUnresolvedGame(Game game) {
        List<Writer> writers = game.getWriters();
        List<Judge> judges = game.getJudges();

        for (Writer w : writers) {
            User u = w.getUser();
            u.getStatistics().recordUnresolved();
            setUserAchievements(u);
            userRepository.save(u);
        }

        for (Judge j : judges) {
            User u = j.getUser();
            u.getStatistics().setWinsAsJudge(u.getStatistics().getWinsAsJudge() + 1);
            setUserAchievements(u);
            userRepository.save(u);
        }
    }
    private void setStats(User user, boolean won, String genre, boolean isSuddenDeath, boolean isUnanimous) {
        UserStatistics stats = user.getStatistics();
        if (won) {
            stats.recordWin(genre, isSuddenDeath, isUnanimous);
        } else {
            stats.recordLoss(isSuddenDeath);
        }
    }

    private void setUserAchievements(User user) {
        UserStatistics stats = user.getStatistics();

        if (stats.getGamesPlayed() == 1) grant(user, AchievementType.ROOKIE_SCRIBE);
        if (stats.getGamesWon() >= 20) grant(user, AchievementType.PUBLISHED_AUTHOR);

        if (stats.getWinsByGenre().getOrDefault("Horror", 0) >= 10)
            grant(user, AchievementType.MASTER_OF_MACABRE);

        if (stats.getUnanimousWins() >= 1) grant(user, AchievementType.CROWD_FAVORITE);
        if (stats.getSuddenDeathWins() >= 1) grant(user, AchievementType.SUDDEN_DEATH_SURVIVOR);

        checkTopPercentile(user);
    }

    private void setGenreMaster(Game game) {
        Story story = game.getStory();
        String genre = story.getWinGenre();
        User winner = story.getWinner();

        GenreMaster gm = genreMasterRepository.findByGenre(genre);
        if (gm == null) {
            gm = new GenreMaster();
            gm.setGenre(genre);
            gm = genreMasterRepository.save(gm);
        }

        int pointsWon = story.getJudges().size();
        Map<Long, Integer> globalVotes = gm.getVotes();

        int currentLifetimeTotal = globalVotes.getOrDefault(winner.getId(), 0);
        globalVotes.put(winner.getId(), currentLifetimeTotal + pointsWon);

        if (!globalVotes.isEmpty()) {
            Long topCandidateId = Collections.max(globalVotes.entrySet(), Map.Entry.comparingByValue()).getKey();

            if (gm.getCurrentMaster() == null || !gm.getCurrentMaster().getId().equals(topCandidateId)) {
                User newMaster = userRepository.findById(topCandidateId).orElse(null);
                gm.setCurrentMaster(newMaster);
            }
        }

        genreMasterRepository.save(gm);
    }

    private void grant(User user, AchievementType type) {
        String achievementName = type.name();
        boolean alreadyHas = user.getAchievements().stream()
                .anyMatch(ua -> ua.getAchievement().getName().equals(achievementName));

        if (!alreadyHas) {
            Achievement ach = achievementRepository.findByName(achievementName);
            if (ach != null) {
                UserAchievement ua = new UserAchievement();
                ua.setUser(user);
                ua.setAchievement(ach);
                user.getAchievements().add(ua);
            }
        }
    }

    private void checkTopPercentile(User user) {
        long totalUsers = userRepository.count();
        if (totalUsers < 10) return;

        int userHorrorWins = user.getStatistics().getWinsByGenre().getOrDefault("Horror", 0);
        long playersBetterThanMe = userRepository.findAll().stream()
                .filter(u -> u.getStatistics().getWinsByGenre().getOrDefault("Horror", 0) > userHorrorWins)
                .count();

        if ((double) playersBetterThanMe / totalUsers <= 0.01 && userHorrorWins > 0) {
            grant(user, AchievementType.HORROR_LEGEND);
        }
    }
}