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

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Main class of the LabPower3005 application.
 *
 * @author Jan Lochmatter <jan@janlochmatter.ch>
 */
public class LabPower extends JFrame {

    /**
     * Main Routine Generates a Object of {@code LabPower} and checks for the
     * existence of the JSSC Library
     *
     * @param args the command line arguments, which are ignored
     */
    public static void main(String[] args) {

        // Set System look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException | UnsupportedLookAndFeelException ex) {
            getLogger().log(Level.WARNING, "Can't set system look and feel", ex);
        }

        // Check if JSSC library is available
        try {
            Class.forName("jssc.SerialPort");
        } catch (ClassNotFoundException ex) {
            // JSSC Library not found! Show info and Exit!
            getLogger().log(Level.SEVERE, "JSSC Library not found", ex);
            JOptionPane.showMessageDialog(null, "JSSC Library not found, "
                    + "this is mandatory for serial communication!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Scheduling the GUI-creation task
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LabPower().setVisible(true);
            }
        });

    }

    /**
     * Creates the Main Window
     */
    public LabPower() {
        initComponents();

        // Fill port list
        comboPorts.removeAllItems();
        for (String s : Communication.getPortNames()) {
            comboPorts.addItem(s);
        }
        comboPorts.setEnabled(true);

        // Handle connection button ////////////////////////////////////////////
        buttonConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String selectedPort = comboPorts.getSelectedItem().toString();
                getLogger().log(Level.FINE, "Try connecting to: {0}", selectedPort);

                // Open connection, getInstance will create a new Instance here
                Communication.getInstance().connect(selectedPort);
                Communication.getInstance().setGuiUpdater(makeGuiUpdater());
                Communication.getInstance().addPropertyChangeListener(makeDisconnectListener());

                // Change GUI elements
                buttonConnect.setEnabled(false);
                comboPorts.setEnabled(false);
                buttonDisconnect.setEnabled(true);
                buttonOutput.setEnabled(true);
            }
        });

        // Handle disconnection button /////////////////////////////////////////
        buttonDisconnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getLogger().log(Level.FINE, "Try to disconnect");
                Communication.getInstance().cancel(true);
            }
        });

        // Handle output on/off button /////////////////////////////////////////
        buttonOutput.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    // Activate output
                    Communication.getInstance().addCommand(
                            new Command(Command.Type.OUT, true));
                } else {
                    // Deactivate output
                    Communication.getInstance().addCommand(
                            new Command(Command.Type.OUT, false));
                }
            }
        });

        // Implement Slider Handling ///////////////////////////////////////////
        panelAdjustV.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Communication.getInstance().addCommand(
                        new Command(Command.Type.VOLTAGE_SET,
                                panelAdjustV.getSetValue()));
            }
        });
        panelAdjustC.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Communication.getInstance().addCommand(
                        new Command(Command.Type.CURRENT_SET,
                                panelAdjustC.getSetValue()));
            }
        });
    }

    /**
     * Generates a GUI-Updater for received answers. It has to be attached to
     * the communication instance. It will be executed from there.
     *
     * @return The GUI-Updater
     */
    private ActionListener makeGuiUpdater() {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Command command = (Command) e.getSource();
                switch (command.getType()) {
                    case VOLTAGE_IS:
                        panelAdjustV.setValue(
                                Double.parseDouble(command.getAnswer()));
                        break;
                    case CURRENT_IS:
                        panelAdjustC.setValue(
                                Double.parseDouble(command.getAnswer()));
                        break;
                    case VOLTAGE_SET:
                        panelAdjustV.setSetValue(
                                Double.parseDouble(command.getAnswer()));
                        break;
                    case CURRENT_SET:
                        panelAdjustC.setSetValue(
                                Double.parseDouble(command.getAnswer()));
                        break;
                    default:
                        break;
                }
            }
        };
    }

    /**
     * Generate a listener for case of disconnection. It has to be attached to
     * the communication instance. It will be triggered from there.
     *
     * @return The disconnection listener
     */
    private PropertyChangeListener makeDisconnectListener() {
        return new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() == SwingWorker.StateValue.DONE) {
                    // Worker thread stopped, port is closed

                    // Change GUI elements
                    buttonConnect.setEnabled(true);
                    comboPorts.setEnabled(true);
                    buttonDisconnect.setEnabled(false);
                    buttonOutput.setEnabled(false);
                    buttonOutput.setSelected(false);

                    // Set Meter readouts to 0
                    panelAdjustC.setSetValue(0);
                    panelAdjustC.setValue(0);
                    panelAdjustV.setSetValue(0);
                    panelAdjustV.setValue(0);
                }
            }
        };
    }

    /**
     * Draws the GUI of the main Window
     */
    private void initComponents() {
        // Miscellaneous window settings
        setTitle("Lab Power 3005");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(0, 1, 5, 5));

        // ********************************************************************
        // Add components
        // Panel Adjust ///////////////////////////////
        panelAdjustV = new PanelAdjust(2, 2, 31.0, Color.BLUE);
        panelAdjustV.setBorder(BorderFactory.createTitledBorder("Adjust Voltage"));
        add(panelAdjustV);

        panelAdjustC = new PanelAdjust(1, 3, 5.1, Color.RED);
        panelAdjustC.setBorder(BorderFactory.createTitledBorder("Adjust Current"));
        add(panelAdjustC);

        // Make subPanel for Setup and Options
        JPanel panelSub1 = new JPanel(new GridLayout(0, 1));
        add(panelSub1);

        // Panel Setup ///////////////////////
        JPanel panelSetup = new JPanel();
        panelSetup.setBorder(BorderFactory.createTitledBorder("Connection Setup"));
        panelSub1.add(panelSetup);

        comboPorts = new JComboBox<>(new String[]{"Wait..."}); // Placeholder
        comboPorts.setEnabled(false);
        panelSetup.add(comboPorts);

        buttonConnect = new JButton("Connect");
        panelSetup.add(buttonConnect);
        buttonDisconnect = new JButton("Disconnect");
        buttonDisconnect.setForeground(Color.RED);
        buttonDisconnect.setEnabled(false);
        panelSetup.add(buttonDisconnect);

        // Panel Options /////////////////////
        JPanel panelOptions = new JPanel();
        panelOptions.setBorder(BorderFactory.createTitledBorder("Options"));
        panelSub1.add(panelOptions);

        buttonOutput = new JToggleButton("Output On/Off");
        buttonOutput.setEnabled(false);
        panelOptions.add(buttonOutput);

        // Menu //////////////////////////////
        JMenuBar menuBar = new JMenuBar();
        JMenu menuFile = new JMenu("File");
        menuBar.add(menuFile);
        JMenu menuHelp = new JMenu("Help");
        menuBar.add(menuHelp);
        setJMenuBar(menuBar);

        menuFile.add(new AbstractAction("Exit") {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        menuHelp.add(new AbstractAction("About") {

            @Override
            public void actionPerformed(ActionEvent e) {
                showAboutDialog();
            }
        });

        // Place on screen ///////////////////////
        pack();
        setLocationRelativeTo(null);    // Center in the screen
        //setMinimumSize(getSize());
        setResizable(false);
        getLogger().log(Level.INFO, "Main Window open");
    }

    /**
     * Shows an About dialog
     */
    private void showAboutDialog() {
        JLabel aboutLabel = new JLabel("<html><center><h1>Lab Power 3005</h1><p/>"
                + "<h2>&copy; 2015 Jan Lochmatter &lt;jan@janlochmatter.ch&gt;</h2></center><p/>"
                + "<p/>"
                + "Serial communicatin is powered by the<p/>"
                + "jSSC (Java Simple Serial Connector) library.<p/>"
                + "<p/>"
                + "This program is free software: you can redistribute it and&#47;or modify<p/>"
                + "it under the terms of the GNU General Public License as published by<p/>"
                + "the Free Software Foundation, either version 3 of the License, or<p/>"
                + "(at your option) any later version.<p/>"
                + "<p/>"
                + "This program is distributed in the hope that it will be useful,<p/>"
                + "but WITHOUT ANY WARRANTY; without even the implied warranty of<p/>"
                + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the<p/>"
                + "GNU General Public License for more details.</html>");
        JOptionPane.showMessageDialog(null, aboutLabel,
                "About", JOptionPane.PLAIN_MESSAGE);
    }

    // Attributes for GUI-Elements ////////////////////////////////////////////
    private JComboBox<String> comboPorts;
    private JButton buttonConnect;
    private JButton buttonDisconnect;
    private JToggleButton buttonOutput;
    private PanelAdjust panelAdjustV;
    private PanelAdjust panelAdjustC;

    /**
     * A Logger which is used all over the application. Simplifies the logger
     * calls
     *
     * @return The same logger instance
     */
    public static Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger(LabPower.class.getName());
            // Logger settings 
            logger.setLevel(Level.ALL);
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.ALL);
            }
        }

        return logger;
    }

    private static Logger logger;
}
