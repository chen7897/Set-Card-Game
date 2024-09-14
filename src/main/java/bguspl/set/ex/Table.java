package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.LinkedList;
import java.util.Queue;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {
    
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // add data structures that contains the tokens of the players
    protected Vector<Vector<Integer>> playersTokens; // tokens per player (if any)


    class Notification {
        int id = -1; // Default to -1 indicating no player is notifying

        public Notification(int pid) {
            id = pid;
        }
    }

    // make a queue of notifications
    protected Queue<Notification> notifications = new LinkedList<>();
    
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        playersTokens = new Vector<Vector<Integer>>(env.config.tableSize);
        for (int i = 0; i < env.config.tableSize; i++)
            playersTokens.add(new Vector<Integer>()); 
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int card) {
        int slot = cardToSlot[card];
        Vector<Integer> playersAtSlot = playersTokens.get(slot);

        synchronized (playersAtSlot)
        {
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}

            while (!playersAtSlot.isEmpty())
            {
                removeToken(playersAtSlot.elementAt(0), slot);
            }

            cardToSlot[card] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
        }
    }

    public void removeCards(int[] cards) {
        for (int card : cards)
            removeCard(card);
    }


    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) { 
        synchronized (playersTokens.get(slot))
        {
            playersTokens.get(slot).add(player);
            env.ui.placeToken(player, slot);    
            playersTokens.get(slot).notifyAll();  
        }
    }

    private boolean containToken(int player, int slot) {
        return playersTokens.get(slot).contains(player);
    }

    /**
     * Returns the tokens of a player.
     * @param player - the player to get the tokens of.
     * @return       - an array of the tokens of the player.
     */
    public int[] getTokens(int player) {
       
        int [] tokens = new int[env.config.featureSize];
        int tokenPlace = 0;
        for (int i = 0 ; ((i < env.config.tableSize) && (tokenPlace < env.config.featureSize)); i++)
        {
            if (playersTokens.get(i).contains(player))
            {
                tokens[tokenPlace] = slotToCard[i];
                tokenPlace++;
            }
        }
        if (tokenPlace < env.config.featureSize)
        {
            for (int i = tokenPlace ; i < env.config.featureSize ; i++)
            {
                tokens[i] = -1;
            }
        }
        return tokens; 
    }

    public void removeAllTokens() {
        synchronized (playersTokens)
        {
            for (int j = 0; j < playersTokens.size(); j++)
            {
                while (!playersTokens.elementAt(j).isEmpty())
                {
                    env.ui.removeToken(playersTokens.elementAt(j).elementAt(0), j);
                    playersTokens.elementAt(j).remove(0);
                }
            }
        }
    }

    public boolean isSlotEmpty(int slot) {
        return slotToCard[slot] == null;
    }

    public void removeTokens (int [] cards)
    {
        int slot;
        for (int i = 0 ; i < cards.length ; i++)
        {
            // the current slot of the card
            slot = cardToSlot[cards[i]];
            // remove the token 
            while (!playersTokens.get(slot).isEmpty())
            {
                removeToken(playersTokens.get(slot).elementAt(0), slot);
            }
        }
    }



    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) { 
        synchronized (playersTokens.get(slot))
       {
            if (containToken(player, slot))
            {
                
                playersTokens.get(slot).remove(Integer.valueOf(player));
                env.ui.removeToken(player, slot);
                playersTokens.get(slot).notifyAll();
                return true;
            }
            playersTokens.get(slot).notifyAll();
            return false;
       }
    }

    public void removeAllCards() {
        for (int i = 0; i < slotToCard.length; i++)
        {
            if (slotToCard[i] != null)
            {
                removeCard(slotToCard[i]);
            }
        }
    }

    public void cleanNotifications() {
        notifications.clear();
    }
}