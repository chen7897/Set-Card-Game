package bguspl.set.ex;
import bguspl.set.Env;
import bguspl.set.ex.Table.Notification;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.LogRecord;

//import com.sun.media.jfxmedia.logging.Logger;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    protected boolean inPenalty = false;
    protected boolean inPoint = false;

    private ArrayBlockingQueue<Integer> keyPresses;

    private int numOfTokens = 0;

    private long timeToWaitAI = 10;

    private Dealer dealer;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.keyPresses = new ArrayBlockingQueue<Integer>(env.config.featureSize);
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            //if the player point activatd
            if (inPoint) {
                point();
                inPoint = false;
            }
            //if the player pennalized activatd
            else if (inPenalty) {
                penalty();
                inPenalty = false;
            }
            try
            {
                synchronized(keyPresses){
                    keyPresses.wait();
                }
            }
            catch (InterruptedException e) {}
            
            if (!keyPresses.isEmpty() && !inPenalty && !inPoint) 
            {
                //pull the action from the queue
                int slot;
                try {
                    slot = keyPresses.take();
                    //try to add it into the table using the function doKeyPress
                    doKeyPress(slot);
                } catch (InterruptedException e) {}
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if (!inPenalty && !inPoint)
                {   
                    int randomSlot = randomKeyPress();
                    keyPressedAi(randomSlot);
                    try{
                        Thread.sleep(timeToWaitAI);
                    }catch (InterruptedException e) {}
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private int randomKeyPress() {
        return (int) (Math.random() * env.config.tableSize);
    }

    public int getId() {
        return id;
    }

     /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        playerThread.interrupt();
        terminate = true;
    }

    /**
     * This method is called when a key is pressed. put the slot into actions
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        //add the slot into the blocking queue
        //wake up the player using the blocking queue
        if (!inPenalty && !inPoint && human)
        {
            synchronized(keyPresses){
                try {
                    if(keyPresses.size() < env.config.featureSize){
                        keyPresses.put(slot);
                    }
                } catch (InterruptedException e) {}
                keyPresses.notify();
            }
        }
    }

        /**
     * This method is called when a key is pressed. put the slot into actions
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressedAi(int slot) {
        //add the slot into the blocking queue
        //wake up the player using the blocking queue
        if (!inPenalty && !inPoint && !dealer.isCheckSet())
        {
            synchronized(keyPresses){
                try {
                    if(keyPresses.size() < env.config.featureSize){
                        keyPresses.put(slot);
                    }
                } catch (InterruptedException e) {}
                keyPresses.notify();
            }
        }
    }

    private void doKeyPress(int slot) 
    {
        if (table.slotToCard[slot] != null) 
        {
            //tries to remove the token from the table returns true if the token was removed
            boolean isTokenRemoved = table.removeToken(id, slot);
            //if the token was not removed and the number of tokens is less than the feature size
            if (!isTokenRemoved && (numOfTokens < env.config.featureSize))
            {
                if (table.slotToCard[slot] != null)
                {
                    //place the token in the table
                    table.placeToken(id, slot);
                    numOfTokens++;

                    //if the player formed a set
                    if(numOfTokens == env.config.featureSize)
                    {
                        synchronized(table.notifications){
                            Table.Notification nf = table.new Notification(id);
                            table.notifications.add(nf);
                            table.notifications.notify();
                        }
                    }
                }
                else
                {
                    cleanQueue();
                }
            }
            //if the token was removed
            else if (isTokenRemoved)
            {
                numOfTokens--;
            } 
        }
        else
        {
            cleanQueue();
        }
    }
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //wait for the point freeze time
        waitFor(env.config.pointFreezeMillis);
        //set the score in the ui
        env.ui.setScore(id, ++score);
        //resets the queue and the number of tokens
        numOfTokens = 0;
        cleanQueue();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        //wait for the penalty freeze time
        waitFor(env.config.penaltyFreezeMillis);
    }

    private void waitFor(long timeToWait) {
        long finishTime = System.currentTimeMillis() + timeToWait-10;
        while (finishTime >= System.currentTimeMillis()) 
        {
            long timeRemaining = finishTime - System.currentTimeMillis();
            env.ui.setFreeze(id, timeRemaining + 1000);
            try { 
                Thread.sleep(1000); 
            } catch (InterruptedException ignored) {}
        }
        env.ui.setFreeze(id, 0);
    }

    //function get called when the player is penalized by the dealer
    public void givePenalty() {
        inPenalty = true;
        synchronized(keyPresses){
            keyPresses.notify();
            }
    }

    //function get called when the player is awarded by the dealer
    public void givePoint() {
        inPoint = true;
        synchronized(keyPresses){
            keyPresses.notify();
            }
    }

    private void cleanQueue() {
        keyPresses.clear();
    }

    public void resetTokens() {
        numOfTokens = 0;
        cleanQueue();
    }

    public void reduceTokens() {
        numOfTokens--;
    }

    public int score() {
        return score;
    }
}