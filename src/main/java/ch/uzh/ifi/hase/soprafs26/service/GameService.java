package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GamePhase;
import ch.uzh.ifi.hase.soprafs26.repository.StoryRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


@Service
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GameStreamService gameStreamService;
    private final GameCleanupService gameCleanupService;
    private final StatsAchvsService statsAchvsService;
    private final QuoteService quoteService;
    private static final int time_reductions_per_writer = 1;
    private static final double REDUCE_TIME_FRACTION = 0.5;

    
    private final List<String> abbreviations = new ArrayList<>(List.of( "z.b", "bzw", "usw", "etc", "d.h", "u.a", "ca", "vgl",
    "dr", "prof", "hr", "fr", "nr", "bspw", "evtl", "ggf", "inkl", "jr", "sr", "st", "mio", "mrd", "mr", "mrs", "ms", "vs", "e.g", "i.e"));

    @Autowired
    public GameService(GameRepository gameRepository, UserService userService, UserRepository userRepository, StoryRepository storyRepository, QuoteService quoteService, GameCleanupService gameCleanupService, GameStreamService gameStreamService, StatsAchvsService statsAchvsService) {
        this.gameRepository = gameRepository;
        this.storyRepository = storyRepository;
        this.userService = userService;
        this.userRepository=userRepository;
        this.quoteService = quoteService;
        this.gameCleanupService = gameCleanupService;
        this.gameStreamService = gameStreamService;
        this.statsAchvsService = statsAchvsService;
    }

    public Game getGame(Long id, String bearerToken) {
        String token = userService.extractToken(bearerToken);
        Game playedGame=getandCheckGame(id, token);
        User requestingUser=getandCheckUser(token);
        boolean partOfGame=false;
        long now = System.currentTimeMillis();
        long timeoutMillis = 15000L;
        for (Writer writer : playedGame.getWriters()) {
            if(writer.getUser().getId().equals(requestingUser.getId())){
                partOfGame=true;
                writer.setLastSeenAt(now);
            }
        }
        for (Judge judge : playedGame.getJudges()) {
            if(judge.getUser().getId().equals(requestingUser.getId())){
                partOfGame=true;
                judge.setLastSeenAt(now);
            }
        }
        if (!partOfGame){
           throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not part of game"); //Check 403 
        }
       
        
        //checkIfPlayerDisconnected(playedGame, timeoutMillis, now);
        if (playedGame.getWriters().size()!=2 || playedGame.getJudges().isEmpty()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erroneous Game State"); //Check 400
        }
        resolveExpiredTurnIfNeeded(playedGame);
        gameRepository.saveAndFlush(playedGame);
        return playedGame;
    }

    private void checkIfPlayerDisconnected(Game playedGame, Long timeoutMillis, Long now){
        for (Writer writer : playedGame.getWriters()) {
            if (now - writer.getLastSeenAt() > timeoutMillis) {
                gameCleanupService.deleteGameAndFlush(playedGame); //we need to outsource this because of transactional that would rollback the whole thing after the exception
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game ended because a writer disconnected");
                
            }
        }  
        List<Judge> disconnectedJudges = new ArrayList<>();
        for (Judge judge : playedGame.getJudges()) {
            if (now - judge.getLastSeenAt() > timeoutMillis) {
               disconnectedJudges.add(judge);
            }
        }
        if (!disconnectedJudges.isEmpty()) {
            playedGame.getJudges().removeAll(disconnectedJudges);
            if (playedGame.getJudges().isEmpty()){
            gameCleanupService.deleteGameAndFlush(playedGame);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game ended because judge disconnected");
            }
            else{
                gameRepository.save(playedGame);
            }
        }
    }
      
    private String truncateToLastSentence(String input) {
        if (input == null) return "";
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) return "";

        //go through the characters from end to beginning to search for the last proper sentence ending 
        int i = trimmedInput.length() - 1;
        while (i >= 0) {

            char c = trimmedInput.charAt(i);

            if (c == '!' || c == '?') {
                return trimmedInput.substring(0, i + 1).trim();
            }

            if (c == '.') {

                //check for a ... ending
                if (i >= 2 && trimmedInput.charAt(i - 1) == '.' && trimmedInput.charAt(i - 2) == '.') {
                    return trimmedInput.substring(0, i + 1).trim();
                }

                // if theres a letter before and right after the ., were gonna consider it part of an abbreviation and not a sentence ending
                boolean beforeIsLetter = (i > 0) && Character.isLetter(trimmedInput.charAt(i - 1));
                boolean afterIsLetter = (i < (trimmedInput.length() - 1)) && Character.isLetter(trimmedInput.charAt(i + 1));
                if (beforeIsLetter && afterIsLetter) {
                    i--;
                    continue;
                }

                //scan for known abbreviations like Dr. or etc. 
                int potentialAbbrevStart = i - 1;
                while ( (potentialAbbrevStart >= 0) && (Character.isLetter(trimmedInput.charAt(potentialAbbrevStart)) || trimmedInput.charAt(potentialAbbrevStart) == '.')) {
                    potentialAbbrevStart--;
                }  
                potentialAbbrevStart++; //if we would stop at >0, then we wouldn't check, if char at 0 is even a letter or .
                String potentialAbbrev = trimmedInput.substring(potentialAbbrevStart, i).toLowerCase();
                if (abbreviations.contains(potentialAbbrev)) {
                    i=potentialAbbrevStart;
                    continue;
                }

                return trimmedInput.substring(0, i + 1).trim();
            }

            i--;
        }

        //in case we did not find a potential sentence ending, we don't want to return half a sentence
        return "";
    }

    // stats patch2: count words, or actually just all the things that are separated by whitespaces in our case, because how to identifiy actual words?
    private long countWords(String text) {
        if (text == null) return 0L;
        String trimmed = text.trim();
        if (trimmed.isBlank()) return 0L;
        // \s is the whitespaces of all sort class (counts " " as well as \n and stuff) and \s+ counts all whitespaces after each other, so that we do not split after each seperate " "
        long count = 0;
        for (String token : text.trim().split("\\s+")) {
            boolean probablyAWord =false;
            for (int i = 0; i < token.length(); i++) {
                if (Character.isLetterOrDigit(token.charAt(i))) { //only count "words" that contain a letter or digit
                    probablyAWord=true;
                }
            }
            if (probablyAWord){
                count++;
            }
        
        }
        return count;
        // double \ because the java compiler doesn't know \s and would complain about an illegal escape sequence, but \\ is and the compiler makes it a \ and regex engine will then know \s+ as an escape sequence
    }

    // helper method for both manual and auto submit of writer input
    private void addInputToStory(Game playedGame, Writer writer, String input) {
        String clean = (input == null) ? "" : input.trim();

        if (playedGame.getPhase() == GamePhase.SUDDEN_DEATH) {
            // Force exactly one sentence for the tie breaker
            clean = truncateToFirstSentence(clean);
        } else {
            if (clean.length() > 2000) {
                clean = clean.substring(0, 2000);
                clean = truncateToLastSentence(clean);
            }
        }

        // enforce maximum writer input length
        if (clean.length() > 2000) {
            clean = clean.substring(0, 2000);
            clean=truncateToLastSentence(clean);
        }

        Story story = playedGame.getStory();
        if (story == null) {
            story = new Story();
            playedGame.setStory(story);
        }
        

        if (!clean.isBlank()) {
            story.addContribution(writer.getUser().getId(), clean);
             // stats patch2 : call the count words statistics method each time we add a writer draft to the story
            User contributor = writer.getUser();
            if (contributor != null && contributor.getStatistics() != null) {
                contributor.getStatistics().addWordsWritten(countWords(clean)); //countwords is the method above that counts the "words" inside the text
            }
        }

        writer.setText("");
        playedGame.setRoundResolved(true);
        playedGame.nextRound();
    }

    private String truncateToFirstSentence(String input) {
        if (input.isEmpty()) return "";
        int end = -1;
        char[] terminators = {'.', '!', '?'};
        for (char t : terminators) {
            int index = input.indexOf(t);
            if (index != -1 && (end == -1 || index < end)) {
                end = index;
            }
        }
        return (end != -1) ? input.substring(0, end + 1).trim() : input;
    }

    private void resolveExpiredTurnIfNeeded(Game playedGame) {
        if (playedGame == null) return;
        if (playedGame.getPhase() != GamePhase.WRITING) return;
        if (playedGame.isRoundResolved()) return;
        if ( (playedGame.getTurnStartedAt() == null) || (playedGame.getTimer() == null)) return;

        //check if timer for this turn already expired
        long now = System.currentTimeMillis();
        long turnEndsAt = playedGame.getTurnStartedAt() + playedGame.getTimer() * 1000;
        if (now < turnEndsAt) return;

        //search for the active writer in this game 
        Writer activeWriter = null;
        for (Writer writer : playedGame.getWriters()) {
            if (writer.getTurn()) {
                activeWriter = writer;
                break;
            }
        }
        if (activeWriter == null) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "No active writer found");
        }

        // shorten writers draft to the last sentence ending
        String inputWithProperEnding = truncateToLastSentence(activeWriter.getText());
        addInputToStory(playedGame, activeWriter, inputWithProperEnding);

        gameRepository.saveAndFlush(playedGame);
    }

    public Game insertWriterInput(Long id, Integer player, String inputText, String bearerToken) {

        // currently player turned out to be useless, but maybe it's useful later, so decided to keep it

        String token = userService.extractToken(bearerToken);
        Game playedGame = getandCheckGame(id, token);
        User requestingUser = getandCheckUser(token);

        if (playedGame.getPhase() != GamePhase.WRITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Users may not write anymore");
        }

        if (playedGame.isRoundResolved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current round already resolved");
        }

        Writer requestingWriter = null;
        for (Writer writer : playedGame.getWriters()) {
            if (writer.getUser().getId().equals(requestingUser.getId())) {
                requestingWriter = writer;
                break;
            }
        }

        if (requestingWriter == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a writer in game");
        }

        if (!requestingWriter.getTurn()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "It's not this writers turn!");
        }

        String prettyInput = (inputText == null) ? "" : inputText.trim();

        addInputToStory(playedGame, requestingWriter, prettyInput);
        return gameRepository.saveAndFlush(playedGame);
    }

    @Scheduled(fixedDelay = 2000) //Spring Annotation for automatically calling this every 2 seconds (2 seconds after the end of the last method call, not start) from the moment the app and spring started. 
    public void terminateExpiredTurns() {
        for (Game game : gameRepository.findAll()) {
            if (game.getPhase() != GamePhase.WRITING) continue;

            int currentRound = game.getCurrentRound();

            try { 
                resolveExpiredTurnIfNeeded(game); //check if timer is expired and if yes, resolve the round
            } catch (Exception e) {
                continue; //if something goes wrong, we just wanna skip this game but continue checking the rest of the games
            }

            //check if we had a round turn, and if yes, inform all the clients that a new round has started
            if (game.getCurrentRound() != currentRound) {
                gameStreamService.sendGameToAllClients(game);
            }
        }
    }

    /* Hard cleanup: deletes any game that has existed longer than the maximum
     possible legitimate game duration. We don't care what the players are doing
     (could be forgetting the game, a crashing laptop, could be away, doesn't matter): past this point the
     game is simply deleted. Deliberately generous so we never kill a slow
     but still valid game.*/
    @Scheduled(fixedDelay = 60_000) // every minute
    public void cleanupOldGames() {
        long now = System.currentTimeMillis();
        for (Game game : gameRepository.findAll()) {
            if (game.getStartedAt() == null) continue;

            /*  Max legitimate duration: writing phase + sudden death + voting +
            result modal, all multiplied by 2 for safety margin*/
            long writingMs = (long) game.getMaxRounds() * game.getTimer() * 1000L;
            long maxDurationMs = (writingMs + 60_000L + 70_000L + 20_000L) * 2L;

            if (now - game.getStartedAt() > maxDurationMs) {
                try {
                    deleteGame(game);
                } catch (Exception e) {
                    // skip, retry next tick
                }
            }
        }
    }

    public void exitGame(Long id, String bearerToken) {
        String token = userService.extractToken(bearerToken);
        Game playedGame=getandCheckGame(id, token);
        User requestingUser=getandCheckUser(token);

        boolean partOfGame = false;
        Writer writerToRemove = null;

        for (Writer writer : playedGame.getWriters()) {
            if (writer.getUser().getId().equals(requestingUser.getId())) {
                partOfGame = true;
                writerToRemove = writer;
                break;
            }
        }

        if (writerToRemove != null) {
            playedGame.getWriters().remove(writerToRemove);
        }

        Judge judgeToRemove = null;

        for (Judge judge : playedGame.getJudges()) {
            if (judge.getUser().getId().equals(requestingUser.getId())) {
                partOfGame = true;
                judgeToRemove = judge;
                break;
            }
        }

        if (judgeToRemove != null) {
            playedGame.getJudges().remove(judgeToRemove);
        }
        if (!partOfGame){
           throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not part of game"); //Check 403 
        }

        if (playedGame.getWriters().size()<2 || playedGame.getJudges().size()<1 ){
            gameRepository.delete(playedGame);
            gameRepository.flush();
            gameStreamService.sendGameDeletedToAllClients(playedGame.getId());
        }
        else{
            gameRepository.save(playedGame);
        }
    }

        public Game saveWriterDraft(Long id, String inputText, String bearerToken) {
        String token = userService.extractToken(bearerToken);
        Game playedGame = getandCheckGame(id, token);
        User requestingUser = getandCheckUser(token);

        Writer requestingWriter = null;
        for (Writer writer : playedGame.getWriters()) {
            if (writer.getUser().getId().equals(requestingUser.getId())) {
                requestingWriter = writer;
                break;
            }
        }

        if (requestingWriter == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a writer in game");
        }

        if (!requestingWriter.getTurn()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "It's not this writers turn!");
        }

        String prettyInput = (inputText == null) ? "" : inputText;

        if (prettyInput.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Input too long");
        }

        requestingWriter.setText(prettyInput);
        gameRepository.saveAndFlush(playedGame);
        return playedGame;
    }

    public Game getandCheckGame(Long id, String token){
     Game playedGame= gameRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, //Check 404
                "Error: A game with that id could not be found"
            ));
         if (token==null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"); //Check 401
        }
        return playedGame;
    }
    public User getandCheckUser(String token){
        User requestingUser = userRepository.findByToken(token);
        if (requestingUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"); //Check 401
        }
        return requestingUser;
    }




    private Map<Long, Map<Judge, Writer>> gameVotes = new HashMap<>();

 
    Map<Long, Map<Judge, Writer>> getGameVotes() {
        return gameVotes;
    }


    public Game getGame(Long gameId){
        String baseErrorMessage = "Error: The provided id: %s is invalid and doesn't match any game.";
		Game gameById = gameRepository.findById(gameId).orElseThrow(() ->  
		new ResponseStatusException(HttpStatus.NOT_FOUND, String.format(baseErrorMessage, gameId))); //NOT_FOUND 404

		return gameById; 
    }

    public int noVote = 0;

    public synchronized void addVote(Game currentGame, Writer voted, Judge judge) {
        noVote++;
        if (voted.getId() == null){
            return;
        }
        gameVotes.computeIfAbsent(currentGame.getId(), k -> new HashMap<>()).put(judge, voted);

        if (allJudgesVoted(currentGame)) {
            this.resolveVoting(currentGame);
        }
    }

   //needed if the judge quietly leaves, so the writers are not stuck for eternity
    public synchronized void forceJudgeAutoVote(Game currentGame, String bearerToken) {
        // check if the caller is a writer in the current game
        String token = userService.extractToken(bearerToken);
        User requestingUser = getandCheckUser(token); //check if user with that token really exists

        boolean isWriter = false;
        for (Writer w : currentGame.getWriters()) {
            if (w.getUser() != null && w.getUser().getId().equals(requestingUser.getId())) {
                isWriter = true;
                break;
            }
        }
        if (!isWriter) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only writers can force a judge auto-vote.");
        }

        //Only allow these force votes in evaluation phase
        if (currentGame.getPhase() != GamePhase.EVALUATION) return;

        // check, if the judge timer has really elapsed
        if (currentGame.getTurnStartedAt() == null || currentGame.getTimer() == null) return;
        long elapsed = (System.currentTimeMillis() - currentGame.getTurnStartedAt()) / 1000;
        if (elapsed < currentGame.getTimer()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Judge timer has not expired yet.");
        }

        // if the round has already been resolved (winner exists or phase advanced), nothing to do
        if (currentGame.getStory() != null && currentGame.getStory().getWinner() != null) return;
        if (currentGame.getPhase() != GamePhase.EVALUATION) return;

        // Force-resolve the round the same way the judge auto vote does
        Writer winner = determineWinner(currentGame);
        updateStory(winner, currentGame);
        finalizeAutoVotedRound(currentGame);
        clearVotes(currentGame);

        gameRepository.saveAndFlush(currentGame);
        
    }
        
    

    private void resolveVoting(Game game) {
        Writer winner = determineWinner(game);

        if (winner == null) {
            // --- 1. DETECT TIE & TRANSITION ---
            game.setPhase(GamePhase.SUDDEN_DEATH);
            game.setTimer(60L); // Short timer for sudden death
            game.setTurnStartedAt(System.currentTimeMillis());
            game.setRoundResolved(false);

            // Fetch the quote for the tie-breaker
            String quote = quoteService.fetchRandomQuote();
            if (quote == null) quote = "One sentence to decide it all.";

            // Store the quote in the story so both writers see it
            game.getStory().setTieBreakerQuote(quote);

            // Prepare writers: Both should be able to write simultaneously
            for (Writer w : game.getWriters()) {
                w.setTurn(true);
                w.setText("");
            }
        }
        else {
            // --- 2. NORMAL RESOLUTION ---
            updateStory(winner, game);

            Map<Judge, Writer> votes = gameVotes.get(game.getId());
            int voteCountForWinner = 0;
            for (Writer v : votes.values()) {
                if (v.getId().equals(winner.getId())) {
                    voteCountForWinner++;
                }
            }
            boolean isUnanimous = (voteCountForWinner == game.getJudges().size());
            statsAchvsService.processGameResults(game, isUnanimous);

            cleanupGame(game);
        }

        // Reset vote counters for the next round (if any) or cleanup
        clearVotes(game);
        gameRepository.saveAndFlush(game);

        // Push the state change to all clients instantly
        gameStreamService.sendGameToAllClients(game);
    }

    // Part 1 correct stats
   
    public void finalizeAutoVotedRound(Game game) {
        Story story = game.getStory();
        //to be safe, because maybe it's null and not a boolean, and then the comparison is still just false
        if (story != null && Boolean.TRUE.equals(story.getHasWinner())) { //just in case of some race condition that judge managed to vote, but not in time, but we would actually have a vote
            statsAchvsService.processGameResults(game, true);
        } else {
            statsAchvsService.processUnresolvedGame(game);
        }
        cleanupGame(game);
    }

   
   

    public boolean allJudgesVoted(Game currentGame) {
        return noVote == currentGame.getJudges().size();
    }

    public void clearVotes(Game currentGame) {
        noVote = 0;
        gameVotes.remove(currentGame.getId());
    }

    public Writer determineWinner(Game currentGame) {
        Map<Judge, Writer> votes = gameVotes.get(currentGame.getId());
        if (votes == null || votes.isEmpty()) {return null;}
        Map<Writer, Integer> voteCounts = new HashMap<>();
        for (Writer writer : votes.values()) {
            voteCounts.merge(writer, 1, Integer::sum);
        }

        Writer winner = null;
        int maxVotes = 0;
        boolean tie = false;

        for (Map.Entry<Writer, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winner = entry.getKey();
                tie = false;
            } else if (entry.getValue() == maxVotes) {
                tie = true;
            }
        }

        if (tie || winner == null) return null;
        return winner;
    }

    public Judge getJudgeFromUser(User userJudge, Game currentGame){
        String baseErrorMessage = "Error: You are not allowed to vote for this game.";
        for (Judge judge : currentGame.getJudges()){
            if(userJudge.getId().equals(judge.getUser().getId())){
                return judge;
            }
        } 
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, baseErrorMessage);
    }


    public Writer findWriterFromId(Long id, Game currentGame){
        String baseErrorMessage = "Error: You are not allowed to vote for a non writer.";
        for (Writer writer : currentGame.getWriters()){
            if(writer.getUser().getId().equals(id)){
                return writer;
            }
        }  
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, baseErrorMessage);
    }

    public Writer getWriterFromUser(User userWriter, Game currentGame){
        String baseErrorMessage = "Error: You are not allowed to vote for a non writer.";
        for (Writer writer : currentGame.getWriters()){
            if(userWriter.getId().equals(writer.getUser().getId())){
                return writer;
            }
        }  
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, baseErrorMessage);
    }


    public User findUserFromToken(String token) {
       
		User userByToken = userRepository.findByToken(token);

		String baseErrorMessage = "Error: You are not Authorized.";
		if (userByToken == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, baseErrorMessage);
		}

		return userByToken;
	}

    public void checkGameIsOver(Game currentGame){
    String baseErrorMessage = "Error: The game is not over.";
    if(!currentGame.getPhase().equals(GamePhase.EVALUATION)){
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, baseErrorMessage); 
        }
    }

    public Story updateStory(Writer winner, Game currentGame) {
        Story story = currentGame.getStory();
        Writer actualWinner = winner;
        Writer loser;


        if (actualWinner == null) {
            actualWinner = currentGame.getWriters().get(0);
            loser = currentGame.getWriters().get(1);
        } else {

            Long winnerUserId = actualWinner.getUser().getId();
            Long firstWriterUserId = currentGame.getWriters().get(0).getUser().getId();

            if (java.util.Objects.equals(winnerUserId, firstWriterUserId)) {
                loser = currentGame.getWriters().get(1);
            } else {
                loser = currentGame.getWriters().get(0);
            }
        }


        story.setWinner(actualWinner.getUser());
        story.setLoser(loser.getUser());
        story.setHasWinner(winner != null);
        story.setWinGenre(actualWinner.getGenre());
        story.setLoseGenre(loser.getGenre());

        List<User> judgeUsers = new ArrayList<>();
        for (Judge judge : currentGame.getJudges()) {
            judgeUsers.add(judge.getUser());
        }
        story.setJudges(judgeUsers);


        storyRepository.save(story);
        gameRepository.save(currentGame);

        return story;
    }


    public void cleanupGame(Game currentGame) {
        currentGame.setPhase(GamePhase.FINISHED);
        gameRepository.save(currentGame);
    }

   public void deleteGame(Game currentGame) {
        Story orphanStory = currentGame.getStory();
        currentGame.getWriters().clear();
        currentGame.getJudges().clear();
        currentGame.setStory(null);
        gameRepository.save(currentGame);
        gameRepository.delete(currentGame);
        gameRepository.flush();

        /*  If the story was never finalized (no winner set, not in any users
        history), it's orphan junk, we delete it. A finalized story has winner != null
        because updateStory() sets it before the game can be deleted via the
        result-modal flow.*/
        if (orphanStory != null && orphanStory.getId() != null && orphanStory.getWinner() == null) {
            try {
                storyRepository.delete(orphanStory);
                storyRepository.flush();
            } catch (Exception e) {
                // best effort
            }
        }
    }

    // 📝 find the active game for the authenticated user (as writer or judge)
    public Game getGameForUser(String bearerToken) {
        User user = userService.findUserFromToken(userService.extractToken(bearerToken));

        return gameRepository.findAll().stream()
                .filter(g ->
                    g.getWriters().stream().anyMatch(w -> w.getUser().getId().equals(user.getId())) ||
                    g.getJudges().stream().anyMatch(j -> j.getUser().getId().equals(user.getId()))
                )
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active game found for this user"));
    }

    

    /*If a user, that is supposed to still be in a game, and tries to start a new one, we delete the old one 
    public void deleteStaleGameForUser(User user) {
        Long userId = user.getId();

        
        for (Game game : gameRepository.findAll()) {

            boolean isWriter = false;
            for (Writer writer : game.getWriters()) {
                if (writer.getUser().getId().equals(userId)) {
                    isWriter = true;
                    break;
                }
            }

            boolean isJudge = false;
            for (Judge judge : game.getJudges()) {
                if (judge.getUser().getId().equals(userId)) {
                    isJudge = true;
                    break;
                }
            }
           
            if (isWriter || isJudge) {
                deleteGame(game);
                return;
            }
        }
    }*/

    public Game assignQuote(Long id, Integer player, String bearerToken) {
        String token = userService.extractToken(bearerToken);
        Game playedGame = getandCheckGame(id, token);
        User requestingUser = getandCheckUser(token);

        getJudgeFromUser(requestingUser, playedGame); // 403 if not a judge

        if (playedGame.getPhase() != GamePhase.WRITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quotes can only be assigned during the WRITING phase");
        }

        if (player == null || (player != 1 && player != 2) || playedGame.getWriters().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid player");
        }

        Writer targetWriter = playedGame.getWriters().get(player - 1);

        if (targetWriter.getQuote() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quote already assigned to this writer");
        }

        int firstTurn = playedGame.getCurrentRound();
        if (!targetWriter.getTurn()) {
            firstTurn += 1;
        }
        int remainingOwnTurns = (firstTurn > playedGame.getMaxRounds())
                ? 0
                : (playedGame.getMaxRounds() - firstTurn) / 2 + 1;
        if (remainingOwnTurns < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough turns left for this writer to use a quote");
        }

        String quote = quoteService.fetchRandomQuote();
        if (quote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch quote from external API");
        }

        targetWriter.setQuote(quote);
        targetWriter.setQuoteAssignedRound(firstTurn);

        gameRepository.saveAndFlush(playedGame);
        return playedGame;
    }

    public Game reduceTime(Long gameId, String bearerToken) {
        String token = userService.extractToken(bearerToken);
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        User requestingUser = getandCheckUser(token);

        if (game.getPhase() != GamePhase.WRITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Time can only be reduced during the WRITING phase");
        }

        boolean isJudge = game.getJudges().stream()
                .anyMatch(j -> j.getUser().getId().equals(requestingUser.getId()));
        if (!isJudge) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only judges can reduce writing time");
        }

        Writer activeWriter = game.getWriters().stream()
                .filter(Writer::getTurn)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No active writer found"));

        if (activeWriter.getReduceTimeReceived() >= time_reductions_per_writer) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reduce-time limit already reached for this writer");
        }

        long elapsedMs = System.currentTimeMillis() - game.getTurnStartedAt();
        long remainingSec = game.getTimer() - (elapsedMs / 1000);

        long reducedTimeSec = Math.round(game.getTimer() * REDUCE_TIME_FRACTION);
        if (remainingSec <= reducedTimeSec) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remaining time is already at or below the reduction threshold");
        }
        long newElapsedMs = (game.getTimer() - reducedTimeSec) * 1000L;
        game.setTurnStartedAt(System.currentTimeMillis() - newElapsedMs);
        activeWriter.setReduceTimeReceived(activeWriter.getReduceTimeReceived() + 1);
        gameRepository.save(game);
        return game;
    }


}

