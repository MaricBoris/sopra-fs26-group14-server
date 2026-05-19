package ch.uzh.ifi.hase.soprafs26.controller;
import ch.uzh.ifi.hase.soprafs26.rest.dto.game.GameInputDTO;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Judge;
import ch.uzh.ifi.hase.soprafs26.entity.Writer;
import ch.uzh.ifi.hase.soprafs26.rest.dto.user.*;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.game.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ch.uzh.ifi.hase.soprafs26.service.GameStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class GameController {

    private final GameService gameService;
    private final GameStreamService gameStreamService;


    GameController(GameService gameService, GameStreamService gameStreamService) {
        this.gameService = gameService;
        this.gameStreamService = gameStreamService;
    }

    @GetMapping("/games/{gameid}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getGame(
            @PathVariable("gameid") Long gameid,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        Game game = gameService.getGame(gameid, bearerToken);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
    }

    @GetMapping(value = "/games/{gameid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public SseEmitter streamGame(
            @PathVariable("gameid") Long gameid,
            @RequestParam("token") String token) {

        String bearerToken = "Bearer " + token;
        gameService.getGame(gameid, bearerToken);
        return gameStreamService.addClient(gameid);
    }

    @PostMapping("/games/{gameid}/input")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO saveWriterInput(
            @PathVariable("gameid") Long gameid,
            @RequestBody GameInputDTO inputDTO,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        Game updatedGame = gameService.insertWriterInput(gameid, inputDTO.getPlayer(),inputDTO.getInput(), bearerToken);
        gameStreamService.sendGameToAllClients(updatedGame);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(updatedGame);
    }

    @PostMapping("/games/{gameid}/draft")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO saveWriterDraft(
            @PathVariable("gameid") Long gameid,
            @RequestBody GameInputDTO inputDTO,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        Game updatedGame = gameService.saveWriterDraft(gameid, inputDTO.getInput(), bearerToken);
        gameStreamService.sendGameToAllClients(updatedGame);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(updatedGame);
    }

    @PostMapping("/games/{gameid}/leave")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void exitGame(
            @PathVariable("gameid") Long gameid,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        gameService.exitGame(gameid, bearerToken);
        
    }


   
    @PostMapping("/games/{gameId}/vote")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO voteGame(@PathVariable Long gameId,
                                @RequestHeader(value = "Authorization") String bearerToken, @RequestBody Long writerId) throws InterruptedException {


        Game currentGame = gameService.getGame(gameId);
        Writer voted = new Writer();

        String token = bearerToken;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (writerId < 0){
        }
        else{
            voted = gameService.findWriterFromId(writerId, currentGame);
        }

        User userJudge = gameService.findUserFromToken(token);

        Judge judge = gameService.getJudgeFromUser(userJudge, currentGame);

        gameService.checkGameIsOver(currentGame);

        gameService.addVote(currentGame, voted, judge);

        long waited = 0L;
        while (!gameService.allJudgesVoted(currentGame) && waited < 70) {
            Thread.sleep(1000);
            waited++;
        }

        if(currentGame.getStory().getWinner() != null){

            gameStreamService.sendGameToAllClients(currentGame);
            return DTOMapper.INSTANCE.convertEntityToGameGetDTO(currentGame);
        }

        Writer winner = gameService.determineWinner(currentGame);

        gameService.updateStory(winner, currentGame);


        gameService.clearVotes(currentGame);

        gameService.cleanupGame(currentGame);

        gameStreamService.sendGameToAllClients(currentGame);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(currentGame);
    }


    @PostMapping("/games/{gameId}/force-judge-vote")
    @ResponseStatus(HttpStatus.OK)
    public void forceJudgeVote(@PathVariable Long gameId,
                            @RequestHeader(value = "Authorization") String bearerToken) {
        Game currentGame = gameService.getGame(gameId);
        gameService.forceJudgeAutoVote(currentGame, bearerToken);
    }

    @DeleteMapping("/games/{gameId}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteGame(@PathVariable Long gameId,
                            @RequestHeader(value = "Authorization") String bearerToken) {
        Game currentGame = gameService.getGame(gameId);
        gameService.deleteGame(currentGame);
        gameStreamService.sendGameDeletedToAllClients(gameId);
    }

    // 📝 GET /games/current, returns the active game for the authenticated user
    // 📝 used by non-leader players to get gameId after room is dissolved on game start
    @GetMapping("/games/current")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getCurrentGame(@RequestHeader(value = "Authorization") String bearerToken) {
        Game game = gameService.getGameForUser(bearerToken);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
    }

    @GetMapping("/games/{gameid}/quotes")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO fetchQuote(@PathVariable("gameid") Long gameid, @RequestParam("player") Integer player,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        Game game = gameService.assignQuote(gameid, player, bearerToken);
        gameStreamService.sendGameToAllClients(game);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
    }

    @PostMapping("/games/{gameId}/reduce-time")
    @ResponseStatus(HttpStatus.OK)
    public GameGetDTO reduceTime(
            @PathVariable Long gameId,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        Game game = gameService.reduceTime(gameId, bearerToken);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
    }    
}

