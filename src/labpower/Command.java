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

import java.util.IllegalFormatException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A command which is sent to the power supply
 *
 * @author Jan Lochmatter <jan@janlochmatter.ch>
 */
public class Command {

    /**
     * Enumeration with all possible command types
     */
    static enum Type {

        VOLTAGE_SET, CURRENT_SET, VOLTAGE_IS, CURRENT_IS, OUT
    }

    // Attributes
    private final Type type;    // Type of command
    private final String writeCommand, // Strings according to the protocol definition
            readCommand;                // expects a "format string"
    private final String answerFormat;  // Expects a regular expression
    private String serialString;        // Formatted string, ready to send
    private final StringBuffer answer;
    private String compiledAnswer;
    private boolean answerExpected;

    /**
     * Creates a query command.
     *
     * @param type Wich type of command
     */
    Command(Type type) {
        this.type = type;
        // Assign the relevant strings according the command type
        switch (type) {
            case VOLTAGE_SET:
                writeCommand = "VSET1:%05.2f";
                readCommand = "VSET1?";
                answerFormat = "\\d\\d[.]\\d\\d";
                break;
            case CURRENT_SET:
                writeCommand = "ISET1:%05.3f";
                readCommand = "ISET1?";
                answerFormat = "\\d[.]\\d\\d\\d";
                break;
            case VOLTAGE_IS:
                writeCommand = null;
                readCommand = "VOUT1?";
                answerFormat = "\\d\\d[.]\\d\\d";
                break;
            case CURRENT_IS:
                writeCommand = null;
                readCommand = "IOUT1?";
                answerFormat = "\\d[.]\\d\\d\\d";
                break;
            case OUT:
                writeCommand = "OUT%1d";
                readCommand = null;
                answerFormat = null;
                break;
            default:
                writeCommand = null;
                readCommand = null;
                answerFormat = null;
                break;
        }

        answer = new StringBuffer(10); // Stringbuffer to accept chars from serial port
        compiledAnswer = null;
        serialString = null; // Null means not yet compiled
        answerExpected = true; // setValue not called
    }

    /**
     * Creates a set command for numeric values.
     *
     * @param type Wich type of command
     * @param value The value to send
     */
    Command(Type type, double value) {
        this(type);

        try {
            serialString = String.format(writeCommand, value);
        } catch (IllegalFormatException ex) {
            logger.log(Level.WARNING, "Can't form write command", ex);
        }
        answerExpected = false;
    }

    /**
     * Creates a set command for boolean values.
     *
     * @param type Wich type of command
     * @param value The value to send
     */
    Command(Type type, boolean value) {
        this(type);

        // Map boolean values
        int decimal;
        if (value) {
            decimal = 1;
        } else {
            decimal = 0;
        }

        try {
            serialString = String.format(writeCommand, decimal);
        } catch (IllegalFormatException ex) {
            logger.log(Level.WARNING, "Can't form write command", ex);
        }
        answerExpected = false;
    }

    /**
     * Get the string which will be sent over serial.
     *
     * @return The formatted string
     */
    String getSerialString() {
        if (serialString == null) { // Read command
            serialString = readCommand;
        }

        return serialString;
    }

    /**
     * Append the received answer from the serial port
     *
     * @param appendString
     * @return <code>true</code> if compile was succesfull
     */
    boolean appendAnswerString(String appendString) {
        answer.append(appendString);
        boolean answerCompiled = false;
        // Try to compile answer
        try {
            Matcher matcher = Pattern.compile(answerFormat).matcher(answer);
            answerCompiled = matcher.find();
            if (answerCompiled) {
                compiledAnswer = matcher.group();
                logger.log(Level.FINE, "Answer string compiled: {0}, from {1}",
                        new Object[]{compiledAnswer, answer.toString()});
            }
        } catch (PatternSyntaxException | IllegalStateException ex) {
            logger.log(Level.FINE, "Can't compile answer string", ex);
        }
        return answerCompiled;
    }

    /**
     * Is an answer expected for this command?
     *
     * @return  <code>true</code> if yes
     */
    boolean getAnswerExpected() {
        return answerExpected;
    }

    /**
     * Get the compiled answer.
     * 
     * @return the Answer
     */
    String getAnswer() {
        return compiledAnswer;
    }

    /**
     * Get Type of this command.
     * 
     * @return The Type
     */
    Type getType() {
        return type;
    }

    // Simplifiy Logger call
    private static final Logger logger = LabPower.getLogger();
}
