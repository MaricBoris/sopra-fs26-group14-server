package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "USER_STATISTICS")
public class UserStatistics implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(mappedBy = "statistics")
    private User user;

    // --- GENERAL STATS ---
    private Integer gamesPlayed = 0;
    private Integer gamesWon = 0;
    private Integer gamesLost = 0;

    // --- STREAKS ---
    private Integer currentWinStreak = 0;
    private Integer highestWinStreak = 0;

    // --- ROLE SPECIFIC ---
    private Integer winsAsWriter = 0;
    private Integer winsAsJudge = 0; // "Wins" as judge could be: voted for the eventual winner
    private Integer totalVotesCast = 0;

    // --- GENRE MASTERY (The Core of S34) ---
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "USER_GENRE_WINS", joinColumns = @JoinColumn(name = "statistics_id"))
    @MapKeyColumn(name = "genre_name")
    @Column(name = "win_count")
    private Map<String, Integer> winsByGenre = new HashMap<>();

    // --- PERFORMANCE STATS ---
    private Integer suddenDeathEntries = 0;
    private Integer suddenDeathWins = 0;
    private Integer unanimousWins = 0; // Won where all judges agreed on you

    // --- LITERARY STATS ---
    private Long totalWordsWritten = 0L;
    private Integer promptAdherenceCount = 0; // If you ever implement a "met the objective" check

    // --- HELPERS FOR UPDATING (Convenience for Service Layer) ---

    public void recordWin(String genre, boolean wasSuddenDeath, boolean wasUnanimous) {
        this.gamesWon++;
        this.gamesPlayed++;
        this.currentWinStreak++;
        if (this.currentWinStreak > this.highestWinStreak) {
            this.highestWinStreak = this.currentWinStreak;
        }

        this.winsAsWriter++;
        this.winsByGenre.put(genre, this.winsByGenre.getOrDefault(genre, 0) + 1);

        if (wasSuddenDeath) {
            this.suddenDeathWins++;
            this.suddenDeathEntries++;
        }
        if (wasUnanimous) {
            this.unanimousWins++;
        }
    }

    public void recordLoss(boolean wasSuddenDeath) {
        this.gamesLost++;
        this.gamesPlayed++;
        this.currentWinStreak = 0;
        if (wasSuddenDeath) {
            this.suddenDeathEntries++;
        }
    }

    // stats patch: currently also resets the win streak, not sure if this is good?
    public void recordUnresolved() {
        this.gamesPlayed++;
        this.currentWinStreak = 0;
    }

    //stats fix2: count the words written correctly
    public void addWordsWritten(long words) {
        if (this.totalWordsWritten == null) {
            this.totalWordsWritten = 0L;
        }
        if (words > 0) {
            this.totalWordsWritten += words;
        }
    }
    
    // --- GETTERS AND SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(Integer gamesPlayed) { this.gamesPlayed = gamesPlayed; }

    public Integer getGamesWon() { return gamesWon; }
    public void setGamesWon(Integer gamesWon) { this.gamesWon = gamesWon; }

    public Integer getGamesLost() { return gamesLost; }
    public void setGamesLost(Integer gamesLost) { this.gamesLost = gamesLost; }

    public Integer getCurrentWinStreak() { return currentWinStreak; }
    public void setCurrentWinStreak(Integer currentWinStreak) { this.currentWinStreak = currentWinStreak; }

    public Integer getHighestWinStreak() { return highestWinStreak; }
    public void setHighestWinStreak(Integer highestWinStreak) { this.highestWinStreak = highestWinStreak; }

    public Integer getWinsAsWriter() { return winsAsWriter; }
    public void setWinsAsWriter(Integer winsAsWriter) { this.winsAsWriter = winsAsWriter; }

    public Integer getWinsAsJudge() { return winsAsJudge; }
    public void setWinsAsJudge(Integer winsAsJudge) { this.winsAsJudge = winsAsJudge; }

    public Integer getTotalVotesCast() { return totalVotesCast; }
    public void setTotalVotesCast(Integer totalVotesCast) { this.totalVotesCast = totalVotesCast; }

    public Map<String, Integer> getWinsByGenre() { return winsByGenre; }
    public void setWinsByGenre(Map<String, Integer> winsByGenre) { this.winsByGenre = winsByGenre; }

    public Integer getSuddenDeathEntries() { return suddenDeathEntries; }
    public void setSuddenDeathEntries(Integer suddenDeathEntries) { this.suddenDeathEntries = suddenDeathEntries; }

    public Integer getSuddenDeathWins() { return suddenDeathWins; }
    public void setSuddenDeathWins(Integer suddenDeathWins) { this.suddenDeathWins = suddenDeathWins; }

    public Integer getUnanimousWins() { return unanimousWins; }
    public void setUnanimousWins(Integer unanimousWins) { this.unanimousWins = unanimousWins; }

    public Long getTotalWordsWritten() { return totalWordsWritten; }
    public void setTotalWordsWritten(Long totalWordsWritten) { this.totalWordsWritten = totalWordsWritten; }

    public Integer getPromptAdherenceCount() { return promptAdherenceCount; }
    public void setPromptAdherenceCount(Integer promptAdherenceCount) { this.promptAdherenceCount = promptAdherenceCount; }
}