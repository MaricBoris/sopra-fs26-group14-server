package ch.uzh.ifi.hase.soprafs26.rest.dto.user;

import ch.uzh.ifi.hase.soprafs26.entity.StoryContribution;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StoryGetDTO {

    private Long id;
    private List<StoryContribution> storyContributions = new ArrayList<>();
    private Boolean hasWinner;
    private String winGenre;
    private String loseGenre;
    private String winnerUsername;
    private String loserUsername;
    private Date creationDate;
    private String objective;
    private String tieBreakerQuote;
    private String title;
    private List<String> judgeUsernames = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public List<StoryContribution> getStoryContributions() { return storyContributions; }
    public void setStoryContributions(List<StoryContribution> storyContributions) { this.storyContributions = storyContributions; }

    public Boolean getHasWinner() { return hasWinner; }
    public void setHasWinner(Boolean hasWinner) { this.hasWinner = hasWinner; }

    public String getWinGenre() { return winGenre; }
    public void setWinGenre(String winGenre) { this.winGenre = winGenre; }

    public String getLoseGenre() { return loseGenre; }
    public void setLoseGenre(String loseGenre) { this.loseGenre = loseGenre; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    public String getLoserUsername() { return loserUsername; }
    public void setLoserUsername(String loserUsername) { this.loserUsername = loserUsername; }

    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date creationDate) { this.creationDate = creationDate; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public String getTieBreakerQuote() { return tieBreakerQuote; }
    public void setTieBreakerQuote(String tieBreakerQuote) { this.tieBreakerQuote = tieBreakerQuote; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getJudgeUsernames() { return judgeUsernames; }
    public void setJudgeUsernames(List<String> judgeUsernames) { this.judgeUsernames = judgeUsernames; }
}