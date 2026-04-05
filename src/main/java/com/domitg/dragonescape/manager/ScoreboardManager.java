package com.domitg.dragonescape.manager;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.model.DragonEscapePlayer;
import com.domitg.dragonescape.model.PlayerClass;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.Collection;

/**
 * Manages the sidebar scoreboard shown to all players in the game.
 */
public class ScoreboardManager {

    private final DragonEscapePlugin plugin;
    private Scoreboard board;
    private Objective objective;

    private static final String TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "DRAGON ESCAPE";

    public ScoreboardManager(DragonEscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        org.bukkit.scoreboard.ScoreboardManager bm =
                Bukkit.getScoreboardManager();
        board = bm.getNewScoreboard();
        objective = board.registerNewObjective("dragonescape", Criteria.DUMMY, TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void destroy() {
        if (board != null) {
            board.getObjectives().forEach(Objective::unregister);
            board = null;
            objective = null;
        }
    }

    /**
     * Updates the scoreboard for all active players.
     *
     * @param players      active game players
     * @param countdown    seconds remaining until game start (or -1 if in game)
     * @param gameTime     seconds elapsed in game (-1 if not started)
     * @param dragonDist   distance from dragon to nearest player
     */
    public void update(Collection<DragonEscapePlayer> players, int countdown,
                       int gameTime, double dragonDist) {
        if (board == null || objective == null) return;

        // Clear existing entries by resetting scores
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        int line = 15;

        setLine(line--, ChatColor.GRAY + "");
        setLine(line--, ChatColor.YELLOW + "Players: " + ChatColor.WHITE
                + players.stream().filter(p -> !p.isEliminated()).count()
                + "/" + players.size());

        if (gameTime >= 0) {
            setLine(line--, ChatColor.YELLOW + "Time: " + ChatColor.WHITE
                    + formatTime(gameTime));
            setLine(line--, ChatColor.YELLOW + "Dragon: "
                    + ChatColor.RED + String.format("%.0f", dragonDist) + " blocks");
        } else if (countdown >= 0) {
            setLine(line--, ChatColor.GREEN + "Starting in: " + ChatColor.WHITE + countdown + "s");
        }

        setLine(line--, ChatColor.GRAY + " ");
        setLine(line--, ChatColor.GOLD + "Classes:");

        for (PlayerClass pc : PlayerClass.values()) {
            long count = players.stream()
                    .filter(p -> p.getPlayerClass() == pc && !p.isEliminated())
                    .count();
            setLine(line--, pc.getColor() + pc.getDisplayName() + ": " + ChatColor.WHITE + count);
        }

        setLine(line--, ChatColor.GRAY + "  ");
        setLine(line--, ChatColor.DARK_GRAY + "dragonescape.mc");
    }

    private void setLine(int score, String text) {
        if (objective == null) return;
        // Truncate to scoreboard limit
        String entry = text.length() > 40 ? text.substring(0, 40) : text;
        Score s = objective.getScore(entry);
        s.setScore(score);
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public void showToPlayer(Player player) {
        if (board != null) {
            player.setScoreboard(board);
        }
    }

    public void resetPlayerScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Updates the scoreboard specifically for one player showing their class and ability cooldown.
     */
    public void updatePlayerLine(DragonEscapePlayer dep) {
        // The sidebar is shared; individual ability cooldown is shown via action bar instead
        // (see GameManager.tickPlayerAbilityBar)
    }
}
