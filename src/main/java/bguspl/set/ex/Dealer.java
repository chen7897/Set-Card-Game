package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Vector;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private long beginningTime;
    private long timer = 0;

    private boolean checkSet = true;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        reshuffleTime = env.config.turnTimeoutMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players)
        {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        beginningTime = System.currentTimeMillis();
        while (!terminate && ((System.currentTimeMillis() - beginningTime) < reshuffleTime)) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (int i = players.length - 1 ; i >= 0 ; i--)  
        {
            players[i].terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        for (int i = 0; i < env.config.tableSize; i++)
        {
            if (table.isSlotEmpty(i))
            {
                if (!deck.isEmpty())
                {
                    table.placeCard(deck.get(0), i);
                    deck.remove(0);
                }
                else {
                    checkSet = false;
                    return;
                }
            }
        }
        checkSet = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized(table.notifications)
            {
                timer = System.currentTimeMillis() - beginningTime;
                long currentTime = env.config.turnTimeoutMillis - timer;
                if (currentTime > env.config.turnTimeoutWarningMillis)
                    table.notifications.wait(100);
                else
                    table.notifications.wait(1);
                if (!table.notifications.isEmpty()){
                    //check if the player who found the set is still in the game
                    if (!checkSet)
                    {
                        checkSet = true;
                        int id = table.notifications.poll().id;                        
                        checkPlayerSet(id);
                        table.notifications.notify();
                    }
                }
            }
        } catch (InterruptedException ignore) {}
    }

public void checkPlayerSet(int id) {
    int[] set = table.getTokens(id);
    if (set[set.length-1] == -1)
        return;
    if (env.util.testSet(set)){
        players[id].givePoint();
        for (int i = 0; i < set.length; i++)
        {
            Vector<Integer> playerWithTokens = table.playersTokens.get(table.cardToSlot[set[i]]);
            for (int player : playerWithTokens)
            {
                players[player].reduceTokens();
            }
        }
        table.removeCards(set);
        updateTimerDisplay(true);
    }else{
        players[id].givePenalty();
    }
}

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
        {
            beginningTime = System.currentTimeMillis();
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        timer = System.currentTimeMillis() - beginningTime;
        long currentTime = env.config.turnTimeoutMillis - timer;
        if (currentTime > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(currentTime, false);
        else
            env.ui.setCountdown(currentTime, true);
    }

    public boolean isCheckSet() {
        return checkSet;
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table.playersTokens)
        {
            checkSet = true;
            table.cleanNotifications();
            table.removeAllTokens();
            for (Player player : players)
                player.resetTokens();
            for (int i = 0; i < env.config.tableSize; i++) 
            {
                if(table.slotToCard[i]!=null) {
                    deck.add(table.slotToCard[i]);
                    table.removeCard(table.slotToCard[i]);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = 0;
        int count = 0;
        for (Player player : players)
        {
            if (player.score() > max) 
                max = player.score();
        }
        for (Player player : players)
        {
            if (player.score() == max) {
                count++;
            }       
        }
        int [] winners = new int[count];
        for (Player player : players)
        {
            if (player.score() == max) {
                winners[count-1] = player.getId();
                count--;
            }       
        }
        env.ui.announceWinner(winners);
        terminate();
    }
}