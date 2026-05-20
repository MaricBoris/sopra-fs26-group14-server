package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import ch.uzh.ifi.hase.soprafs26.entity.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.achvs.AchievementGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.achvs.GenreMasterGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.achvs.UserAchievementGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.game.GameGetDTO;

import ch.uzh.ifi.hase.soprafs26.rest.dto.room.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.room.RoomGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.room.RoomPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.user.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.stats.UserStatisticsGetDTO;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity (e.g., the User) to the external/API representation (e.g.,
 * UserGetDTO for getting, UserPostDTO for creating)
 * and vice versa.
 * Additional mappers can be defined for new entities.
 * Always created one mapper for getting information (GET) and one mapper for
 * creating information (POST).
 */
@Mapper
public interface DTOMapper {

	DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

    @Mapping(source = "username", target = "username")
	@Mapping(source = "password", target = "password")
    @Mapping(source = "bio", target = "bio")
	User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "username", target = "username")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "creationDate", target = "creationDate")
	UserGetDTO convertEntityToUserGetDTO(User user);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "creationDate", target = "creationDate")
    @Mapping(source = "token", target = "token")
    UserPersonalGetDTO convertEntityToUserPersonalGetDTO(User user);

    @Mapping(source = "name", target = "name")
    Room convertRoomPostDTOtoEntity(RoomPostDTO roomPostDTO);

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "turn", target = "turn")
    @Mapping(source = "genre", target = "genre")
    @Mapping(source = "genreDescription", target = "genreDescription")
    @Mapping(source = "text", target = "text")
    @Mapping(source = "quote", target = "quote")
    @Mapping(source = "quoteAssignedRound", target = "quoteAssignedRound")
    WriterGetDTO convertEntityToWriterGetDTO(Writer writer);

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "insertions", target = "insertions")
    JudgeGetDTO convertEntityToJudgeGetDTO(Judge judge);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "playerCount", target = "playerCount")
    @Mapping(source = "lobbyLeader", target = "lobbyLeader")
    @Mapping(source = "users", target = "users")
    @Mapping(source = "writers", target = "writers")
    @Mapping(source = "judges", target = "judges")
    @Mapping(source = "chat", target = "chat")
    @Mapping(source = "timer", target = "timer")
    @Mapping(source = "maxRounds", target = "maxRounds")
    RoomGetDTO convertEntityToRoomGetDTO(Room room);

    @Mapping(source = "username", target = "username")
    @Mapping(source = "message", target = "message")
    @Mapping(source = "timestamp", target = "timestamp")
    ChatMessageGetDTO convertEntityToChatMessageGetDTO(ChatMessage chatMessage);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "storyContributions", target = "storyContributions")
    @Mapping(source = "hasWinner", target = "hasWinner")
    @Mapping(source = "winGenre", target = "winGenre")
    @Mapping(source = "loseGenre", target = "loseGenre")
    @Mapping(source = "winner.username", target = "winnerUsername")
    @Mapping(source = "loser.username", target = "loserUsername")
    @Mapping(source = "objective", target = "objective")
    @Mapping(source = "tieBreakerQuote", target = "tieBreakerQuote")
    @Mapping(source = "title", target = "title")
    @Mapping(expression = "java(story.getJudges().stream().map(u -> u.getUsername()).collect(java.util.stream.Collectors.toList()))", target = "judgeUsernames")
    StoryGetDTO convertEntityToStoryGetDTO(Story story);

    @Mapping(source = "id", target = "gameId")
    @Mapping(source = "writers", target = "writers")
    @Mapping(source = "judges", target = "judges")
    @Mapping(source = "timer", target = "timer")
    @Mapping(source = "turnStartedAt", target = "turnStartedAt")
    @Mapping(source = "story", target = "story")
    @Mapping(source = "currentRound", target = "currentRound")
    @Mapping(source = "phase", target = "phase")
    @Mapping(source = "maxRounds", target = "maxRounds")
    @Mapping(expression = "java(game.getTimer() != null ? Math.round(game.getTimer() * 0.5) : 45L)", target = "reducedTimeThreshold")
    GameGetDTO convertEntityToGameGetDTO(Game game);

    @Mapping(source = "gamesPlayed", target = "gamesPlayed")
    @Mapping(source = "gamesWon", target = "gamesWon")
    @Mapping(source = "gamesLost", target = "gamesLost")
    @Mapping(source = "currentWinStreak", target = "currentWinStreak")
    @Mapping(source = "highestWinStreak", target = "highestWinStreak")
    @Mapping(source = "winsAsWriter", target = "winsAsWriter")
    @Mapping(source = "winsAsJudge", target = "winsAsJudge")
    @Mapping(source = "totalVotesCast", target = "totalVotesCast")
    @Mapping(source = "winsByGenre", target = "winsByGenre")
    @Mapping(source = "suddenDeathEntries", target = "suddenDeathEntries")
    @Mapping(source = "suddenDeathWins", target = "suddenDeathWins")
    @Mapping(source = "unanimousWins", target = "unanimousWins")
    @Mapping(source = "totalWordsWritten", target = "totalWordsWritten")
    UserStatisticsGetDTO convertEntityToUserStatisticsGetDTO(UserStatistics statistics);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "displayName", target = "displayName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "icon", target = "icon")
    AchievementGetDTO convertEntityToAchievementGetDTO(Achievement achievement);

    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "id", target = "id")
    @Mapping(source = "achievement", target = "achievement")
    @Mapping(source = "unlockedAt", target = "unlockedAt")
    @Mapping(source = "isDisplayed", target = "isDisplayed")
    UserAchievementGetDTO convertEntityToUserAchievementGetDTO(UserAchievement userAchievement);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "genre", target = "genre")
    @Mapping(source = "currentMaster", target = "currentMaster")
    @Mapping(expression = "java(genreMaster.getVotes().size())", target = "totalVotesCast")
    GenreMasterGetDTO convertEntityToGenreMasterGetDTO(GenreMaster genreMaster);
}
