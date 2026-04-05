package com.domitg.dragonescape.model;

/**
 * Represents the current state of a Dragon Escape game.
 */
public enum GameState {
    /**
     * The game is in the lobby waiting for players.
     */
    WAITING,

    /**
     * The countdown to start the game is in progress.
     */
    STARTING,

    /**
     * The game is actively running.
     */
    IN_GAME,

    /**
     * The game has ended and is cleaning up.
     */
    ENDING
}
