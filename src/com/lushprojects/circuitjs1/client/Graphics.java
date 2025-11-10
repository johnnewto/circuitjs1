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

package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.dom.client.Context2d;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Graphics {
	
	Context2d context;
	int currentFontSize;
	Color lastColor;
	int savedFontSize;
	static boolean isFullScreen=false;
	
	// Batch drawing optimization
	private boolean batchMode = false;
	private int batchedOperations = 0;
	
	// Text rendering cache
	private static class TextCacheEntry {
	    double width;
	    long timestamp;
	}
	private static HashMap<String, TextCacheEntry> textWidthCache = new HashMap<String, TextCacheEntry>();
	private static final int TEXT_CACHE_SIZE = 500;
	private static final long TEXT_CACHE_LIFETIME_MS = 5000; // 5 seconds
	
	  public Graphics(Context2d context) {
		    this.context = context;
		    currentFontSize = 12;
	  }
	  
	  public void setColor(Color color) {
		    if (color != null) {
		      String colorString = color.getHexValue();
		      context.setStrokeStyle(colorString);
		      context.setFillStyle(colorString);
		    } else {
		      System.out.println("Ignoring null-Color");
		    }
		    lastColor=color;
		  }
	  
	  public void setColor(String color) {
	      context.setStrokeStyle(color);
	      context.setFillStyle(color);
	      lastColor=null;
	  }
	  
	  public void clipRect(int x, int y, int width, int height) {
		  context.beginPath();
		  context.rect(x, y, width, height);
		  context.clip();
	  }
	  
	  public void restore() {
	      context.restore();
	      currentFontSize = savedFontSize;
	  }
	  public void save() {
	      context.save();
	      savedFontSize = currentFontSize;
	  }
	  
	  
	  public void fillRect(int x, int y, int width, int height) {
		//  context.beginPath();
		  context.fillRect(x, y, width, height);
		//  context.closePath();
	  }
	  
	  public void drawRect(int x, int y, int width, int height) {
		//  context.beginPath();
		  context.strokeRect(x, y, width, height);
		//  context.closePath();
	  }
	  
	  public void fillOval(int x, int y, int width, int height) {
		  context.beginPath();
		  context.arc(x+width/2, y+width/2, width/2, 0, 2.0*3.14159);
		  context.closePath();
		  context.fill();
	  }
	  
	  public void drawString(String str, int x, int y) {
	      context.fillText(str, x, y);
	  }
	  
	  public double measureWidth(String s) {
		  // Check cache first
		  TextCacheEntry entry = textWidthCache.get(s);
		  long currentTime = System.currentTimeMillis();
		  
		  if (entry != null && currentTime - entry.timestamp < TEXT_CACHE_LIFETIME_MS) {
			  return entry.width;
		  }
		  
		  // Not in cache or expired, measure it
		  double width = context.measureText(s).getWidth();
		  
		  // Add to cache
		  if (textWidthCache.size() >= TEXT_CACHE_SIZE) {
			  // Cache full, clear old entries
			  clearOldTextCacheEntries(currentTime);
		  }
		  
		  entry = new TextCacheEntry();
		  entry.width = width;
		  entry.timestamp = currentTime;
		  textWidthCache.put(s, entry);
		  
		  return width;
	  }
	  
	  // Clear text cache entries older than the cache lifetime
	  private void clearOldTextCacheEntries(long currentTime) {
		  Iterator<Map.Entry<String, TextCacheEntry>> iterator = textWidthCache.entrySet().iterator();
		  while (iterator.hasNext()) {
			  Map.Entry<String, TextCacheEntry> entry = iterator.next();
			  if (currentTime - entry.getValue().timestamp >= TEXT_CACHE_LIFETIME_MS) {
				  iterator.remove();
			  }
		  }
		  
		  // If still too big after removing old entries, clear half the cache
		  if (textWidthCache.size() >= TEXT_CACHE_SIZE) {
			  int toRemove = TEXT_CACHE_SIZE / 2;
			  iterator = textWidthCache.entrySet().iterator();
			  while (iterator.hasNext() && toRemove > 0) {
				  iterator.next();
				  iterator.remove();
				  toRemove--;
			  }
		  }
	  }
	  
	  // Clear the entire text width cache (call when font changes or for debugging)
	  public static void clearTextCache() {
		  textWidthCache.clear();
	  }
	  
	  public void setLineWidth(double width){
		  context.setLineWidth(width);
	  }
	  
	  /**
	   * Start batched drawing mode for performance optimization.
	   * Reduces context.beginPath()/stroke() calls by batching multiple operations.
	   * Must call endBatch() when done.
	   */
	  public void startBatch() {
		  if (!batchMode) {
			  batchMode = true;
			  batchedOperations = 0;
			  context.beginPath();
		  }
	  }
	  
	  /**
	   * End batched drawing mode and flush all batched operations.
	   */
	  public void endBatch() {
		  if (batchMode) {
			  if (batchedOperations > 0) {
				  context.stroke();
			  }
			  batchMode = false;
			  batchedOperations = 0;
		  }
	  }
	  
		public void drawLine(int x1, int y1, int x2, int y2) {
			if (batchMode) {
				// In batch mode, add disconnected line segments to the current path
				context.moveTo(x1, y1);  // Always moveTo for disconnected lines
				context.lineTo(x2, y2);
				batchedOperations++;
			} else {
				// Normal mode - individual draw calls
				context.beginPath();
				context.moveTo(x1, y1);
				context.lineTo(x2, y2);
				context.stroke();
			}
		}

	  public void drawLine(Point x1, Point x2) {
		  drawLine(x1.x, x1.y, x2.x, x2.y);
	  }

	  public void drawPolyline(int[] xpoints, int[] ypoints, int n) {
		  int i;
		  context.beginPath();
		  for (i=0; i<n;i++){
			  if (i==0)
				  context.moveTo(xpoints[i],ypoints[i]);
			  else
				  context.lineTo(xpoints[i],ypoints[i]);
		  }
		  context.closePath();
		  context.stroke();
	  }
	
	  
	  public void fillPolygon(Polygon p) {
		  int i;
		  context.beginPath();
		  for (i=0; i<p.npoints;i++){
			  if (i==0)
				  context.moveTo(p.xpoints[i],p.ypoints[i]);
			  else
				  context.lineTo(p.xpoints[i],p.ypoints[i]);
		  }
		  context.closePath();
		  context.fill();
		  }
	  
	  public void setFont(Font f){
		  if (f!=null){
			  context.setFont(f.fontname);
			  currentFontSize=f.size;
			  // Clear cache when font changes since widths will be different
			  clearTextCache();
		  }
	  }

//	  Font getFont(){
//	          // this may return wrong font if g.save/restore() is used.  just use that instead
//		  return currentFont;
//	  }
	  
	  public void drawLock(int x, int y) {
	      context.save();
	      setColor(new Color(209,75,75));
	      context.setLineWidth(3);
	      fillRect(x,y,30,20);
	      context.beginPath();
	      context.moveTo(x+15-10, y);
	      context.lineTo(x+15-10 , y -4);
	      context.arc(x+15,y-4,10,-3.1415, 0);
	      context.lineTo(x+15+10, y);
	      context.stroke();
	      context.restore();
	  }
	  
	   static int distanceSq(int x1, int y1, int x2, int y2) {
	    	x2 -= x1;
	    	y2 -= y1;
	    	return x2*x2+y2*y2;
	        }
	  
	   void setLineDash(int a, int b) {
	       setLineDash(context, a, b);
	   }
	   
	   native static void setLineDash(Context2d context, int a, int b) /*-{
	       if (a == 0)
	           context.setLineDash([]);
	       else
	       	   context.setLineDash([a, b]);
	   }-*/;
	   
	   void setLetterSpacing(String spacing) {
	       setLetterSpacingNative(context, spacing);
	   }
	   
	   native static void setLetterSpacingNative(Context2d context, String spacing) /*-{
	       context.letterSpacing = spacing;
	   }-*/;
	   
	   
	   public static void viewFullScreen() {
	       requestFullScreen();
	       isFullScreen=true;
	   }
	   
	   private native static void requestFullScreen() /*-{
	   var element = $doc.documentElement;

	   if (element.requestFullscreen) {
	     element.requestFullscreen();
	   } else if (element.mozRequestFullScreen) {
	     element.mozRequestFullScreen();
	   } else if (element.webkitRequestFullscreen) {
	     element.webkitRequestFullscreen();
	   } else if (element.msRequestFullscreen) {
	     element.msRequestFullscreen();
	   }
	 }-*/;
	   
	   public static void exitFullScreen() {
	       requestExitFullScreen();
	       isFullScreen=false;
	   }
	   
	   private native static void requestExitFullScreen() /*-{
	   var d = $doc;

	   if (d.exitFullscreen) {
	     d.exitFullscreen();
	   } else if (d.mozExitFullScreen) {
	     d.mozExitFullScreen();
	   } else if (d.webkitExitFullscreen) {
	     d.webkitExitFullscreen();
	   } else if (d.msExitFullscreen) {
	     d.msExitFullscreen();
	   }
	 }-*/;
	   
	   
}
