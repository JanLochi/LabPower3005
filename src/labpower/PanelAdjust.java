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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.Hashtable; // Obsolete, but still good practice
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Panel for Voltage/Current adjust
 *
 * @author Jan Lochmatter <jan@janlochmatter.ch>
 */
class PanelAdjust extends JPanel {

    // Attributes
    private MeterPanel lcdDisp;
    private JSlider slider;
    int nrDecExp;

    /**
     * Creates a new JPanel with LCD-Style display for setpoint and actual
     * value.
     *
     * @param nrPreDecDigits before decimal point
     * @param nrDec Digits after decimal point
     * @param maxValue The maximum possible value of the powersupply
     * @param meterColor The fontcolor
     */
    PanelAdjust(final int nrPreDec, final int nrDec,
            final double maxValue, Color meterColor) {
        // Use BoxLayout for Gaps
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(Box.createVerticalStrut(5));

        // LCD Style Display
        lcdDisp = new MeterPanel(nrPreDec, nrDec, meterColor);
        add(lcdDisp);
        add(Box.createVerticalStrut(5));

        // Slider
        nrDecExp = (int) Math.pow(10, nrDec);
        int majorTickSpacing = ((int) Math.round(maxValue / 6)) * nrDecExp;
        int max = (int) (maxValue * nrDecExp);
        //Create the label table
        Hashtable labelTable = new Hashtable(); // Obsolete, but still good practice
        for (int labelIndex = 0; labelIndex <= max; labelIndex += majorTickSpacing) {
            labelTable.put(labelIndex,
                    new JLabel("" + (labelIndex / nrDecExp), JLabel.CENTER));
        }
        slider = new JSlider(JSlider.HORIZONTAL, max, 0);
        slider.setLabelTable(labelTable);
        slider.setMajorTickSpacing(majorTickSpacing);
        slider.setPaintTicks(true);
        slider.setMinorTickSpacing((int) Math.round(majorTickSpacing / 5));
        slider.setPaintLabels(true);

        slider.addChangeListener(new ChangeListener() { // Update LCD Display
            @Override
            public void stateChanged(ChangeEvent e) {
                lcdDisp.setSetValue((double) slider.getValue() / nrDecExp);
            }
        });

        // Add a tooltip
        slider.setToolTipText("<html><b>Hint:</b> Click the sliders far left or"
                + "<p>right edge for fine adjustement.</html>");
        add(slider);
        add(Box.createVerticalStrut(5));

        // TODO: Quick Settings
        //JPanel panelQuick = new JPanel();        
    }

    /**
     * Adds a ChangeListener to the Panel. The listener is attached to the
     * included slider
     *
     * @param l the ChangeListener to add
     */
    final void addChangeListener(ChangeListener l) {
        slider.addChangeListener(l);
    }

    /**
     * Set the value of the powersupply setpoint.
     *
     * @param setValue The setpoint value
     */
    final void setValue(double value) {
        lcdDisp.setValue(value);
    }

    /**
     * Set the value of the powersupply setpoint.
     *
     * @param setValue The setpoint value
     */
    final void setSetValue(double setValue) {
        lcdDisp.setSetValue(setValue);
        slider.setValue((int) (setValue * nrDecExp));
    }

    /**
     * Get the value of the powersupply setpoint.
     *
     * @return The setpoint value
     */
    double getSetValue() {
        return ((double) slider.getValue() / nrDecExp);
    }

    /**
     * Dedicated panel for a value display in LCD Style
     *
     */
    private class MeterPanel extends JPanel {

        // Attributes
        private final int nrPreDec;
        private final int nrDec;
        private final int nrDecExp;
        private final Color meterColor;
        private String valuePreDec;
        private String valueDec;
        private String setValuePreDec;
        private String setValueDec;
        private Dimension prefferedSize;

        // Settings
        private static final float segmentFontSizeBig = 75;
        private static final float segmentFontSizeSmall = 45;
        private static final int margin = 7;

        // Constants, determined in contructor
        private final Font segmentFontBig;
        private final Font segmentFontSmall;

        /**
         * Creates new LCD-Style Display.
         *
         * @param nrPreDec Digits before decimal point
         * @param nrDec Digits after decimal point
         * @param meterColor The fontcolor
         */
        public MeterPanel(int nrPreDec, int nrDec, Color meterColor) {
            this.nrPreDec = nrPreDec;
            this.nrDec = nrDec;
            nrDecExp = 1 + (int) Math.pow(10, nrDec);
            this.meterColor = meterColor;

            setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));

            // LCD-Style font
            Font f;
            try {
                // Load Font from resources
                f = Font.createFont(Font.TRUETYPE_FONT,
                        getClass().getResourceAsStream(
                                "/resources/fonts/Segment7Standard.otf"));
            } catch (FontFormatException | IOException ex) {
                logger.log(Level.WARNING, "LCD-Font not found, "
                        + "Fallback to systemfont", ex);
                // Fallback to systemfont
                f = new Font(Font.MONOSPACED, Font.PLAIN, 10);
            }
            // Construct the font's in two sizes
            segmentFontBig = f.deriveFont(segmentFontSizeBig);
            segmentFontSmall = f.deriveFont(segmentFontSizeSmall);

            // Initialize placeholder values
            setValue(0);
            setSetValue(0);

        }

        /**
         * Set to the actual reading of the powersupply
         *
         * @param value The reading value
         */
        final void setValue(double value) {
            // Format the value Strings
            valuePreDec = String.format("%0" + nrPreDec + "d", (int) value);
            valueDec = String.format("%0" + nrDec + "d",
                    (int) ((nrDecExp) * (value - (int) value)));

            repaint();
        }

        /**
         * Set the value of the powersupply setpoint.
         *
         * @param setValue The setpoint value
         */
        final void setSetValue(double setValue) {
            // Format the value Strings
            setValuePreDec = String.format("%0" + nrPreDec + "d", (int) setValue);
            setValueDec = String.format("%0" + nrDec + "d",
                    (int) ((nrDecExp) * (setValue - (int) setValue)));

            repaint();
        }

        /**
         * Overwriten paint method. All drawing of the Digits happens here
         *
         * @param g the <code>Graphics</code> object to protect
         * @see #paint
         */
        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int x, y; // Draw positions        

            // Use antialiasing
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Clear panel
            g2.clearRect(0, 0, getWidth(), getHeight());

            // Draw the big number        
            g2.setFont(segmentFontBig);
            g2.setColor(meterColor);

            x = margin;
            y = g2.getFontMetrics().getAscent() + margin;
            g2.drawString(valuePreDec, x, y);
            x += margin + g2.getFontMetrics().stringWidth(valuePreDec);
            g2.fillRect(x, y - 5, 5, 5);
            x += margin + 5;
            g2.drawString(valueDec, x, y);
            x += margin + g2.getFontMetrics().stringWidth(valueDec);
            // Draw the spacer
            g2.setColor(Color.BLACK);
            g2.fillRect(x, y - g2.getFontMetrics().getAscent(), 2,
                    g2.getFontMetrics().getAscent());
            x += margin + 2;
            // Draw the small number
            g2.setColor(meterColor);
            g2.setFont(segmentFontSmall);
            g2.drawString(setValuePreDec, x, y);
            x += margin + g2.getFontMetrics().stringWidth(setValuePreDec);
            g2.fillRect(x, y - 5, 5, 5);
            x += margin + 5;
            g2.drawString(setValueDec, x, y);
            //x += margin + g2.getFontMetrics().stringWidth(setValueDec);

        }

        /**
         * Gets the size of this panel, to fit all digits.
         *
         * @return The components optimal size
         */
        @Override
        public Dimension getPreferredSize() {

            if (prefferedSize == null) {
                Canvas g2 = new Canvas(); // Just for the fontMetrics
                int x, y;

                x = margin;
                y = g2.getFontMetrics(segmentFontBig).getAscent() + margin;
                x += margin + g2.getFontMetrics(segmentFontBig).stringWidth(valuePreDec);
                x += margin + 5;
                x += margin + g2.getFontMetrics(segmentFontBig).stringWidth(valueDec);
                // Draw the spacer        
                x += margin + 2;
                // Draw the small number
                x += margin + g2.getFontMetrics(segmentFontSmall).stringWidth(setValuePreDec);
                x += margin + 5;
                x += margin + g2.getFontMetrics(segmentFontSmall).stringWidth(setValueDec);
                y += margin;

                prefferedSize = new Dimension(x, y);
            }

            return prefferedSize;
        }
    }

    // Simplifiy Logger call
    private static final Logger logger = LabPower.getLogger();
}
