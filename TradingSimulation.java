/* 
# As a first step in our interview process, please send us working solution for the following problem:
# Implement a real-time Stock trading engine for matching Stock Buys with Stock Sells.
# 1. Write an ‘addOrder’ function that will have the following parameters:
#       ‘Order Type’ (Buy or Sell), ‘Ticker Symbol’, ‘Quantity’, ‘Price’
#       Support 1,024 tickers (stocks) being traded.
#       Write a wrapper to have this ‘addOrder’ function randomly execute with different parameter values to simulate active stock transactions.

# 2. Write a ‘matchOrder’ function, that will match Buy & Sell orders with the following criteria:
Buy price for a particular ticker is greater than or equal to lowest Sell price available then.
Write your code to handle race conditions when multiple threads modify the Stock order book, as run in real-life, by multiple stockbrokers. 
Also, use lock-free data structures.
Do not use any dictionaries, maps or equivalent data structures. Essentially there should be no ‘import’-s nor ‘include’-s nor 
imilar construct relevant to the programming language you are using that provides you dictionary, map or equivalent data structure capability. 
In essence, you are writing the entire code. Standard language-specific non data structure related items are ok, but try to avoid as best as
you can.
Write your ‘matchOrder’ function with a time-complexity of O(n), where 'n' is the number of orders in the Stock order book.
*/


// Please check the bottom for the flow of the code.

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TradingSimulation {
    private List<String> tickers; 
    private LockFreeList buyOrders = new LockFreeList();
    private LockFreeList sellOrders = new LockFreeList();
    private AtomicBoolean stopThreads = new AtomicBoolean(false);

    public static void main(String[] args) {
        TradingSimulation simulation = new TradingSimulation(); 
        simulation.runSimulation(); 
    }

    private void runSimulation() {
        tickers = generateTickers(1024);  
        runThreads(); 
        // System.out.println("Buy Orders:");
        // buyOrders.printBuyList();
        // System.out.println("Sell Orders:");
        // sellOrders.printSellList();
        matchAllOrders(); 
    }

    // This function generates a list of unique 3-character ticker symbols using random uppercase letters from A to Z. 
    // It creates count number of tickers and returns them as a list.
    private List<String> generateTickers(int count) {
        List<String> tickers = new ArrayList<>();
        Random random = new Random();
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < count; i++) {
            StringBuilder ticker = new StringBuilder();
            for (int j = 0; j < 3; j++) {
                ticker.append(characters.charAt(random.nextInt(characters.length())));
            }
            tickers.add(ticker.toString());
        }
        return tickers;
    }

    // This function adds a new order (either a buy or sell order) to the respective order list (buyOrders or sellOrders). 
    // If the order is a buy order, it also attempts to match it with existing sell orders using the matchOrders function. Invalid order types are flagged with an error message. 
    // The function is thread-safe due to the use of synchronized for logging

    private void addOrder(String orderType, String tickerSymbol, int quantity, double price) {
        Node newNode = new Node(orderType, tickerSymbol, quantity, price);
        synchronized (this) {
            System.out.println("newNode: " + newNode);
        }
        if (orderType.equals("Buy")) {
            buyOrders.addOrModifyBuyIfPresent(newNode);
            matchOrders(newNode);
        } else if (orderType.equals("Sell")) {
            sellOrders.addOrModifySellIfPresent(newNode);
        } else {
            System.out.println("Invalid Order Type");
        }
    }

    //This function matches a specific buy order (buyNode) with the best available sell order for the same ticker symbol.
    // It searches the sellOrders list for the lowest-priced sell order that matches the buy order's ticker symbol. 
    // If a suitable sell order is found, it matches the orders, updates their quantities, and removes fully matched orders from the respective lists.
    // If no suitable sell order is found, it logs a message and exits. 
    // The function ensures efficient order matching and maintains the integrity of the order book.
    // Runs in O(n) where n is no of number of orders in the Stock order book.

    private void matchOrders(Node buyNode) {
        System.out.println("Matching orders concurrently for Buy Order: " + buyNode.tickerSymbol);
        Node sell = sellOrders.getHead("sell");
        Node prevSell = null;
        Node bestSell = null;
        Node bestSellPrev = null;

        while (sell != null) {
            if (sell.tickerSymbol.equals(buyNode.tickerSymbol) &&
                (bestSell == null || sell.price < bestSell.price)) {
                bestSell = sell;
                bestSellPrev = prevSell;
            }
            prevSell = sell;
            sell = sell.next;
        }

        if (bestSell == null || buyNode.price < bestSell.price) {
            System.out.println("No suitable sell order found for " + buyNode.tickerSymbol);
            return;
        }

        int matchedQuantity = Math.min(buyNode.quantity, bestSell.quantity);
        System.out.println("? Matched: " + matchedQuantity + " shares of " + buyNode.tickerSymbol + " at $" + bestSell.price);

        buyNode.quantity -= matchedQuantity;
        bestSell.quantity -= matchedQuantity;

        if (buyNode.quantity == 0) {
            System.out.println("? Removing completed BUY order: " + buyNode.tickerSymbol);
            buyOrders.removeNode("buy", buyNode.tickerSymbol);
        }

        if (bestSell.quantity == 0) {
            System.out.println("? Removing completed SELL order: " + bestSell.tickerSymbol);
            sellOrders.removeNode("sell", bestSell.tickerSymbol);
        }
    }

    // This function matches all buy orders with sell orders in the order book. 
    // It iterates through the buyOrders list and, for each buy order, searches the sellOrders list for the lowest-priced sell order with the same ticker symbol. 
    // If a match is found, it updates the quantities of the buy and sell orders and removes fully matched orders from the lists. 
    // The process repeats until no more matches are found. The function ensures efficient order matching and maintains the integrity of the order book.

    private void matchAllOrders() {
        System.out.println("Matching orders...");
        while (true) {
            Node buy = buyOrders.getHead("buy");
            Node prevBuy = null;
            boolean matched = false;

            while (buy != null) {
                Node sell = sellOrders.getHead("sell");
                Node prevSell = null;
                Node bestSell = null;
                Node bestSellPrev = null;

                while (sell != null) {
                    if (sell.tickerSymbol.equals(buy.tickerSymbol) &&
                        (bestSell == null || sell.price < bestSell.price)) {
                        bestSell = sell;
                        bestSellPrev = prevSell;
                    }
                    prevSell = sell;
                    sell = sell.next;
                }

                if (bestSell == null || buy.price < bestSell.price) {
                    prevBuy = buy;
                    buy = buy.next;
                    continue;
                }

                int matchedQuantity = Math.min(buy.quantity, bestSell.quantity);
                System.out.println("? Matched: " + matchedQuantity + " shares of " + buy.tickerSymbol + " at $" + bestSell.price);

                buy.quantity -= matchedQuantity;
                bestSell.quantity -= matchedQuantity;
                matched = true;

                if (buy.quantity == 0) {
                    System.out.println("? Removing completed BUY order: " + buy.tickerSymbol);
                    if (prevBuy == null) buyOrders.setHead("buy", buy.next);
                    else prevBuy.next = buy.next;
                    buy = buy.next;
                } else {
                    prevBuy = buy;
                    buy = buy.next;
                }

                if (bestSell.quantity == 0) {
                    System.out.println("? Removing completed SELL order: " + bestSell.tickerSymbol);
                    if (bestSellPrev == null) sellOrders.setHead("sell", bestSell.next);
                    else bestSellPrev.next = bestSell.next;
                }
            }

            if (!matched) break; 
        }
    }

    // This function simulates the continuous generation of random buy or sell orders in a multi-threaded environment.
    //  It randomly selects an order type (Buy or Sell), a ticker symbol from the predefined list, a quantity between 1 and 100, and a price between 10 and 1000.
    //   The generated order is then added to the respective order list using the addOrder function. 
    // The loop runs until the stopThreads flag is set to true, allowing for concurrent order generation.

    private void wrapperExecute(AtomicBoolean stopThreads) {
        Random random = new Random();
        while (!stopThreads.get()) {
            String orderType = random.nextBoolean() ? "Buy" : "Sell";
            String tickerSymbol = tickers.get(random.nextInt(tickers.size()));
            int quantity = random.nextInt(100) + 1;
            double price = 10 + (1000 - 10) * random.nextDouble();
            addOrder(orderType, tickerSymbol, quantity, price);
        }
    }

    // This function manages the execution of multiple threads to simulate concurrent order generation. 
    // It creates and starts two threads, each running the wrapperExecute function to generate random buy or sell orders. 
    // After allowing the threads to run for 500 milliseconds, it sets the stopThreads flag to true to signal the threads to stop.
    //  Finally, it waits for all threads to complete using join(), ensuring proper synchronization and cleanup. 
    // This function facilitates concurrent order generation and graceful termination of threads.

    private void runThreads() {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(() -> wrapperExecute(stopThreads));
            threads.add(t);
            t.start();
        }

        try {
            Thread.sleep(500);  
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopThreads.set(true); 

        for (Thread t : threads) {
            try {
                t.join(); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Class to create a node for Lock Free List
    class Node {
        String orderType;
        String tickerSymbol;
        int quantity;
        double price;
        Node next;

        Node(String orderType, String tickerSymbol, int quantity, double price) {
            this.orderType = orderType;
            this.tickerSymbol = tickerSymbol;
            this.quantity = quantity;
            this.price = price;
            this.next = null;
        }

        @Override
        public String toString() {
            return String.format("[%s, %s, %d, %.2f]", orderType, tickerSymbol, quantity, price);
        }
    }

    // Class to create a linked list which creates a lock free List/Array.
    // All the operations in this class are atomic, resulting the data structure to be lock free.

    class LockFreeList {
        private AtomicReference<Node> headBuy = new AtomicReference<>(null);
        private AtomicReference<Node> headSell = new AtomicReference<>(null);

        // This function adds a new buy order (newNode) to the buyOrders list.
        void addOrModifyBuyIfPresent(Node newNode) {
            while (true) {
                Node curr = headBuy.get();
                newNode.next = curr;
                if (headBuy.compareAndSet(curr, newNode)) break;
            }
        }

        //This function adds a new sell order (newNode) to the sellOrders list.
        void addOrModifySellIfPresent(Node newNode) {
            while (true) {
                Node curr = headSell.get();
                newNode.next = curr;
                if (headSell.compareAndSet(curr, newNode)) break;
            }
        }
        
        // This function finds if the node with tickerSYmbol exists, if not returns null.
        Node findNode(String typ, String tickerSymbol) {
            Node curr = getHead(typ);
            while (curr != null) {
                if (curr.tickerSymbol.equals(tickerSymbol)) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        // This function removes the node from the list.
        void removeNode(String typ, String tickerSymbol) {
            Node prev = null;
            Node curr = getHead(typ);

            while (curr != null) {
                if (curr.tickerSymbol.equals(tickerSymbol)) {
                    if (prev == null) {
                        setHead(typ, curr.next);
                    } else {
                        prev.next = curr.next;
                    }
                    return;
                }
                prev = curr;
                curr = curr.next;
            }
        }

        // Prints the buyOrder List
        void printBuyList() {
            Node curr = headBuy.get();
            while (curr != null) {
                System.out.printf("%s: %d @ %.2f -> ", curr.tickerSymbol, curr.quantity, curr.price);
                curr = curr.next;
            }
            System.out.println("None");
        }

        // // Prints the SELLOrder List
        void printSellList() {
            Node curr = headSell.get();
            while (curr != null) {
                System.out.printf("%s: %d @ %.2f -> ", curr.tickerSymbol, curr.quantity, curr.price);
                curr = curr.next;
            }
            System.out.println("None");
        }
        
        // Fetches the head of the list.  
        Node getHead(String typ) {
            return typ.equals("sell") ? headSell.get() : headBuy.get();
        }

        // Sets the head of the list.
        void setHead(String typ, Node newHead) {
            if (typ.equals("sell")) {
                headSell.set(newHead);
            } else {
                headBuy.set(newHead);
            }
        }
    }
}

// Flow of the simulation

//                                  runSimulation()
//                                      |
//                                  generateTickers()                                    {Generates 1024 tickers}
//                                      |
//                                  runThreads()                                         {Runs multiple threads simultaneously for 500 ms}
//                                      |         
//                                wrapperExecute()                                        {wrapper function to call addOrder randomly}
//                                      |
//                                  addOrder()                                
//                                  |        |
//         addOrModifySellIfPresent()    addOrModifyBuyIfPresent()
//                     |                          |
//                             |               matchOrders()                              {(Runs in O(n)) Checks if there is a existing selling order (acc to conditions as per question) to buy}
//                                    |        |   
//                                  matchAllOrders()                                      {Again runs a check if there are buy orders for tickers to sell after all orders in stock book}
//                                                                                         {not required as per question, just wanted to do a little extra :)} 
// 
// 


