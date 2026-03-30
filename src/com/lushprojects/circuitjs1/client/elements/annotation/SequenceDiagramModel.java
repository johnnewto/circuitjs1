/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client.elements.annotation;

import com.lushprojects.circuitjs1.client.util.Graphics;

import java.util.Vector;

/**
 * Data model classes for sequence diagram representation.
 * Contains participant definitions and drawable diagram elements.
 */
public class SequenceDiagramModel {
    
    // ══════════════════════════════════════════════════════════════════════════
    // PARTICIPANT
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents a participant (actor or box) in the sequence diagram.
     */
    public static class Participant {
        public final String name;     // Display name
        public final String alias;    // Alias for referencing (defaults to name)
        public final boolean isActor; // True = stick figure, False = box
        public int x;                 // Calculated X position (set during layout)
        
        public Participant(String name, boolean isActor) {
            this.name = name;
            this.alias = name;
            this.isActor = isActor;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // DIAGRAM ELEMENTS
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Abstract base class for drawable diagram elements.
     * Subclasses: Message, Divider, Note
     */
    public static abstract class DiagramElement {
        /** Draw the element at the given Y position */
        public abstract void draw(Graphics g, DiagramRenderer renderer, int y);
        
        /** Return the vertical space this element consumes */
        public abstract int getHeight();
    }
    
    /**
     * Represents an arrow message between two participants.
     */
    public static class Message extends DiagramElement {
        public final String from;     // Source participant name
        public final String to;       // Target participant name
        public final String label;    // Message text (may contain \\n for line breaks)
        public final boolean dashed;  // True for --> dashed arrows
        public int messageIndex;      // Index among Message elements (for animation)
        
        public Message(String from, String to, String label, boolean dashed) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.dashed = dashed;
            this.messageIndex = -1;
        }
        
        @Override
        public void draw(Graphics g, DiagramRenderer renderer, int y) {
            renderer.drawMessage(g, this, y);
        }
        
        /**
         * Calculates vertical space needed for label lines.
         */
        public int getLabelBlockHeight() {
            if (label == null || label.isEmpty()) return 10;
            int lineCount = label.split("\\\\n").length;
            return Math.max(10, lineCount * 15 - 6);
        }
        
        @Override
        public int getHeight() {
            return getLabelBlockHeight() + 18;
        }
    }
    
    /**
     * Represents a horizontal divider with centered label.
     */
    public static class Divider extends DiagramElement {
        public final String label;
        
        public Divider(String label) {
            this.label = label;
        }
        
        @Override
        public void draw(Graphics g, DiagramRenderer renderer, int y) {
            renderer.drawDivider(g, this, y);
        }
        
        @Override
        public int getHeight() {
            return 35;
        }
    }
    
    /**
     * Represents a note box attached to a participant or spanning full width.
     */
    public static class Note extends DiagramElement {
        public final String target;       // Participant name or "across"
        public final String position;     // "left", "right", or "across"
        public final Vector<String> lines;
        
        public Note(String target, String position, Vector<String> lines) {
            this.target = target;
            this.position = position;
            this.lines = lines;
        }
        
        @Override
        public void draw(Graphics g, DiagramRenderer renderer, int y) {
            renderer.drawNote(g, this, y);
        }
        
        @Override
        public int getHeight() {
            return lines.size() * 15 + 30;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // HELPER CLASSES
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Helper class for note parsing result.
     */
    public static class NoteStart {
        public String target;
        public String position;
    }
    
    /**
     * Represents the source and target sectors for a flow transaction,
     * extracted from TableElm column analysis.
     */
    public static class FlowEndpoints {
        public String sourceSector;   // Sector with negative value (outflow)
        public String targetSector;   // Sector with positive value (inflow)
        public double flowValue;      // Absolute magnitude of the flow
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PARSED DIAGRAM CONTAINER
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Container for a fully parsed diagram structure.
     */
    public static class ParsedDiagram {
        public Vector<Participant> participants;
        public Vector<DiagramElement> elements;
        public String titleLine1;
        public String titleLine2;
        public int messageCount;
        
        public ParsedDiagram() {
            participants = new Vector<Participant>();
            elements = new Vector<DiagramElement>();
            titleLine1 = null;
            titleLine2 = null;
            messageCount = 0;
        }
        
        /**
         * Finds a participant by name or alias.
         */
        public Participant findParticipant(String name) {
            for (int i = 0; i < participants.size(); i++) {
                Participant p = participants.get(i);
                if (p.name.equals(name) || p.alias.equals(name)) {
                    return p;
                }
            }
            return null;
        }
        
        /**
         * Ensures a participant exists, creating it if necessary.
         */
        public void ensureParticipant(String name) {
            if (findParticipant(name) == null) {
                participants.add(new Participant(name, false));
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // RENDERER INTERFACE
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Interface for rendering diagram elements.
     * Implemented by SequenceDiagramElm to provide drawing context.
     */
    public interface DiagramRenderer {
        /** Draw a message arrow between participants */
        void drawMessage(Graphics g, Message msg, int y);
        
        /** Draw a horizontal divider */
        void drawDivider(Graphics g, Divider divider, int y);
        
        /** Draw a note box */
        void drawNote(Graphics g, Note note, int y);
        
        /** Get diagram width for layout calculations */
        int getDiagramWidth();
        
        /** Get background color */
        String getBgColor();
        
        /** Get divider background color */
        String getDividerBgColor();
        
        /** Get divider border color */
        String getDividerColor();
        
        /** Get line color */
        String getLineColor();
        
        /** Get note background color */
        String getNoteBgColor();
    }
}
