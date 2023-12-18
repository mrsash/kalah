package org.mrsash.kalahapi.service.impl;

import static org.mrsash.kalahapi.model.TurnType.PLAYER_1;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.mrsash.kalahapi.dto.GameCreateDto;
import org.mrsash.kalahapi.dto.GameDto;
import org.mrsash.kalahapi.dto.GameMoveDto;
import org.mrsash.kalahapi.dto.GameMoveResultDto;
import org.mrsash.kalahapi.exception.GameIncorrectTurnException;
import org.mrsash.kalahapi.exception.GameNotFoundException;
import org.mrsash.kalahapi.mapper.IGameEntityMapper;
import org.mrsash.kalahapi.model.GameEntity;
import org.mrsash.kalahapi.repository.IGameRepository;
import org.mrsash.kalahapi.service.IBoardService;
import org.mrsash.kalahapi.service.IGameService;
import org.mrsash.kalahapi.service.IPlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class GameService implements IGameService {

    private final IPlayerService playerService;
    private final IBoardService boardService;
    private final IGameRepository gameRepository;
    private final IGameEntityMapper gameEntityMapper;

    @Autowired
    public GameService(
            IPlayerService playerService,
            IBoardService boardService,
            IGameRepository gameRepository,
            IGameEntityMapper gameEntityMapper
    ) {
        this.playerService = playerService;
        this.boardService = boardService;
        this.gameRepository = gameRepository;
        this.gameEntityMapper = gameEntityMapper;
    }

    @Override
    public GameDto get(String gameId) {
        log.debug("Try to get game with id '{}'", gameId);
        var gameIdAsUuid = UUID.fromString(gameId);
        var result = gameEntityMapper.toDto(gameRepository.findById(gameIdAsUuid)
                .orElseThrow(() -> new GameNotFoundException(gameIdAsUuid)));
        log.debug("Successfully got game with id '{}'", gameId);
        return result;
    }

    @Override
    public List<GameDto> getAllByPlayer(String playerId) {
        log.debug("Try to get all player games with id '{}'", playerId);
        var result = gameRepository.findAllByOwnerId(UUID.fromString(playerId)).stream()
                .map(gameEntityMapper::toDto)
                .toList();
        log.debug("Successfully got all player games with id '{}'", playerId);
        return result;
    }

    @Transactional
    @Override
    public GameDto create(GameCreateDto gameCreateDto) {
        log.debug("Try to create game with next data: '{}'", gameCreateDto);
        var result = gameRepository.save(GameEntity.builder()
                .ownerId(playerService.get(gameCreateDto.getOwnerId()).getId())
                .turn(PLAYER_1)
                .board(boardService.create())
                .build()
        );
        log.debug("Successfully created game with next data: '{}'", result);
        return gameEntityMapper.toDto(result);
    }

    @Transactional
    @Override
    public GameMoveResultDto move(GameMoveDto gameMoveDto) {
        var gameId = gameMoveDto.getGameId();
        var position = gameMoveDto.getPosition();
        var clientTurn = gameMoveDto.getTurn();
        log.debug("Try to make a move (game id = {}, position = {}, turn = {})", gameId, position, clientTurn);
        var game = get(gameId);
        if (clientTurn != game.getTurn()) {
            throw new GameIncorrectTurnException(UUID.fromString(gameId), clientTurn, game.getTurn());
        }
        var result = boardService.calculate(position, game);
        game.setTurn(result.getNextTurn());
        game.setBoard(result.getBoard());
        if (result.isFinished()) {
            gameRepository.deleteById(game.getId());
        } else {
            gameRepository.save(gameEntityMapper.toEntity(game));
        }
        log.debug("Successfully made a move (game id = '{}', board state = {})", gameId, game.getBoard());
        return new GameMoveResultDto(result.isFinished(), game);
    }
}
