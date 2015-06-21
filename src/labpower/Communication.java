/*
 * Copyright (C) 2015 Jan Lochmatter <jan@janlochmatter.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package labpower;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import jssc.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.SwingWorker;

/**
 * Handels all communication over the Serial Port. It's a singleton class, only
 * one object can exist.
 *
 * @author Jan Lochmatter <jan@janlochmatter.ch>
 */
class Communication extends SwingWorker<Void, Command> {

    // Attributes /////////////////////////////////////////////////////////////   
    private SerialPort serial;
    private String portName;
    
    private int compileErrors;  // Count up on errors

    // Lock for pausing Worker Thread
    private final Lock lock = new ReentrantLock();
    private final Condition workerSleep = lock.newCondition();

    // Queues for Commands
    private final Queue<Command> pendingQueue;

    // ActionListener for GUI-Update
    private ActionListener guiUpdater;

    // Timer for polling PowerSupply Parameters
    private Timer pollTimer;

    private Communication() {
        // Initalize Queues
        pendingQueue = new ConcurrentLinkedQueue<>(); // Or LinkedTransferQueue?
        
        compileErrors = 0;
    }

    static Communication getInstance() {
        if (communication == null) {
            communication = new Communication();
        }
        return communication;
    }

    private static Communication communication;

    void setGuiUpdater(ActionListener guiUpdater) {
        this.guiUpdater = guiUpdater;
    }

    /**
     * The Swingworker task which handles communication
     *
     * @return void, as it will never end
     */
    @Override
    protected Void doInBackground() {
        Command command;
        boolean isCompiled;
        String recString;
        boolean isConnected;

        logger.log(Level.INFO, "SwingWorker started");

        // Open port and prepare communication
        serial = new SerialPort(portName);
        try {
            serial.openPort();
            serial.setParams(SerialPort.BAUDRATE_9600,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            serial.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

            // Clear Buffer
            serial.readString();

            isConnected = true;
            logger.log(Level.INFO, "Connection established");
        } catch (SerialPortException ex) {
            // Can't connect
            logger.log(Level.WARNING, "Can't open a connection", ex);
            isConnected = false;
        }

        // Start poll TimerTask
        pollTimer = new Timer("PollTimer");
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                addCommand(new Command(Command.Type.VOLTAGE_IS));
                addCommand(new Command(Command.Type.CURRENT_IS));
            }
        },
                500, // delay ms
                1000); // period ms

        // Prepare pendingQueue        
        pendingQueue.clear(); // Clear queue
        addCommand(new Command(Command.Type.VOLTAGE_SET)); // Get current settings
        addCommand(new Command(Command.Type.CURRENT_SET));

        // Handle the actual communication
        try {
            lock.lock();
            while (!isCancelled() && isConnected) {
                // Flush Pending Queue
                command = pendingQueue.poll(); // Get first element
                if (command != null) {
                    // Send to serialPort
                    sendString(command.getSerialString());
                    // Sleep for some time, powersupply can't handle commands too quick
                    Thread.sleep(50); // Max 30ms?
                    // Get the answer, if it's expected
                    if (command.getAnswerExpected()) {
                        try {
                            recString = serial.readString();
                        } catch (SerialPortException ex) {
                            logger.log(Level.WARNING, "Can't read from Serial Port", ex);
                            recString = null;
                        }

                        // Try to compile answer
                        isCompiled = command.appendAnswerString(recString);
                        if (isCompiled) { // Valid answer is found
                            // Move command to finished List
                            publish(command);
                            
                            // Reduce error counter
                            if (compileErrors != 0) {
                                compileErrors--;
                            }
                        } else {
                            logger.log(Level.WARNING, "Can't compile answer for: "+
                                    command.getSerialString());
                            // Command will be disposed
                            
                            // Check if too many errors occured
                            if(compileErrors++ > 4) {
                                this.cancel(true); // Disconnect
                            }
                        }

                    }
                } else {
                    // Go to sleep, wait for new command
                    workerSleep.await();
                }
            }
        } catch (InterruptedException ex) {

        } finally {
            lock.unlock();
            // End SwingWorker. CleanUP, close conection usw...
            pollTimer.cancel();
            pollTimer = null;
            try {
                serial.closePort();
                serial = null;
                logger.log(Level.INFO, "Connection closed");
            } catch (SerialPortException ex1) {
                logger.log(Level.WARNING, "Can't close Serial Port", ex1);
            }
            communication = null; // Dispose this instance in any case
        }

        return null;
    }

    /**
     * Generates ActionEvents from received commands. The guiUpdater will handle
     * these
     *
     * @param commands is populated from the SwingWorker
     */
    @Override
    protected void process(List<Command> commands) {
        if (guiUpdater != null) {
            // Handle every command which as accumulated in the list
            for (Command command : commands) {
                guiUpdater.actionPerformed(new ActionEvent(command, 0, null));
            }
        } else {
            logger.log(Level.WARNING, "No guiUpdater defined");
        }

        //System.out.println("Elements in Queue: "+pendingQueue.size());
    }

    /**
     * Try to establish a connection on the Serial Port.
     *
     *
     * @param portName The path to the port
     */
    void connect(String portName) {
        this.portName = portName;

        // Start worker
        this.execute();
    }

    /**
     * Add a command object to the queue which will be sent to the powersupply
     *
     * @param command The command to send
     * @see Command
     */
    final void addCommand(Command command) {
        lock.lock();
        try {
            pendingQueue.add(command);
            workerSleep.signal(); // Continue worker Thread
        } catch (IllegalStateException | ClassCastException |
                NullPointerException | IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Can't add command to pendingQueue", ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send an actual String on the serial port
     *
     * @param stringToSend the properly formed string
     */
    private void sendString(String stringToSend) {
        try {
            serial.writeString(stringToSend);
            logger.log(Level.FINE, "Sent to Serial Port: {0}", stringToSend);
        } catch (SerialPortException ex) {
            logger.log(Level.WARNING, "Can't write to Serial Port", ex);
        }
    }

    /**
     * Get sorted array of serial ports in the system. This call is forwardet to
     * the JSSC Lib
     *
     * @return String array. If there is no ports in the system String[] with
     * <b>zero</b> length will be returned
     */
    static String[] getPortNames() {
        return SerialPortList.getPortNames();
    }

    // Simplifiy Logger call
    private static final Logger logger = LabPower.getLogger();
}

            // Attach listener for incomming Messages and handle incoming messages
//            serial.setEventsMask(SerialPort.MASK_RXCHAR); // only received chars
//            serial.addEventListener(new SerialPortEventListener() {
//                @Override
//                public void serialEvent(SerialPortEvent e) {
//                    String recString;
//                    try {
//                        // Add all received bytes to Queue                      
//                        for (int i = e.getEventValue(); i > 0; i--) {
//                            recString = serial.readString(1); // Read just one char
//                            serialRecieveQueue.add(recString);
//                            logger.log(Level.INFO, "Received: {0}", recString);
//                        }
//                    } catch (SerialPortException ex) {
//                        logger.log(Level.WARNING, "Can't read from Serial Port", ex);
//                    }
//
//                }
//            });
//    void flushPendingQueue() {
//        Command command = pendingQueue.poll(); // Get first element
//        while (command != null) {
//            // Send to serialPort
//            sendString(command.getSerialString());
//            // Move to sentQueue if answer is expected
//            if (command.getAnswerExpected()) {
//                sentQueue.add(command);
//            }
//            // Look for next pending command
//            command = pendingQueue.poll();
//            try {
//                // Sleep for a time, powersupply can't handle commands too quick
//                Thread.sleep(30);
//            } catch (InterruptedException ex) {
//                logger.log(Level.SEVERE, "Can't put thread to sleep", ex);
//            }
//        }
//    }
//    void handleSentQueue() {
//        boolean compiled;
//        Command command = sentQueue.peek(); // Get element (not remove)
//        while (command != null) {
//            // Get Char form Queue
//            String recString = serialRecieveQueue.poll();
//            if (recString != null) { // There are chars to handle
//                // Try to compile answer
//                compiled = command.appendAnswerString(recString);
//                if (compiled) { // Valid answer is found
//                    // Move command to finished Queue
//                    finishedQueue.add(sentQueue.poll());
//                    command = sentQueue.peek(); // Get next element
//                }
//            } else {
//                // No new chars to add to commands, exit method
//                command = null;
//            }
//        }
//    }
