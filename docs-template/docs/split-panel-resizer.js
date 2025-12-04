/**
 * Split Panel Layout Controller
 * 
 * Handles the draggable resizer and initializes the split-panel layout.
 * Works together with circuitjs-utils.js for circuit loading.
 * 
 * For parent pages only (money-first-principles.html):
 * - Resizer drag functionality
 * - Responsive vertical/horizontal modes
 * 
 * Usage:
 *   <script src="circuitjs-utils.js"></script>
 *   <script src="split-panel-resizer.js"></script>
 */

(function() {
  'use strict';
  
  /* ==========================================================================
     RESIZER DRAG FUNCTIONALITY
     ========================================================================== */
  
  /**
   * Initialize the draggable resizer between split panels
   * Supports both horizontal (desktop) and vertical (mobile) modes
   */
  function initResizer() {
    var container = document.querySelector('.split-container');
    var leftPanel = document.querySelector('.split-left');
    var resizer = document.querySelector('.split-resizer');
    
    if (!resizer || !leftPanel || !container) {
      return; // Not a split-panel page
    }
    
    // Create overlay to prevent iframe from capturing mouse events during drag
    var overlay = document.createElement('div');
    overlay.className = 'resize-overlay';
    document.body.appendChild(overlay);
    
    var isResizing = false;
    
    /**
     * Check if we're in mobile/vertical mode (narrow viewport)
     * @returns {boolean} True if vertical mode
     */
    function isVerticalMode() {
      return window.innerWidth <= 768;
    }
    
    /**
     * Handle start of resize drag
     * @param {Event} e - Mouse or touch event
     */
    function startResize(e) {
      isResizing = true;
      resizer.classList.add('dragging');
      overlay.classList.add('active');
      document.body.style.userSelect = 'none';
      e.preventDefault();
    }
    
    /**
     * Handle resize drag movement
     * @param {Event} e - Mouse or touch event
     */
    function handleMove(e) {
      if (!isResizing) return;
      
      var containerRect = container.getBoundingClientRect();
      
      if (isVerticalMode()) {
        // Vertical resizing (top/bottom split)
        var clientY = e.touches ? e.touches[0].clientY : e.clientY;
        var newHeight = clientY - containerRect.top;
        var containerHeight = containerRect.height;
        
        // Constrain between 20% and 70% of container height
        var minHeight = containerHeight * 0.2;
        var maxHeight = containerHeight * 0.7;
        var clampedHeight = Math.max(minHeight, Math.min(maxHeight, newHeight));
        
        leftPanel.style.flex = '0 0 ' + clampedHeight + 'px';
      } else {
        // Horizontal resizing (left/right split)
        var clientX = e.touches ? e.touches[0].clientX : e.clientX;
        var newWidth = clientX - containerRect.left;
        var containerWidth = containerRect.width;
        
        // Constrain between 20% and 80% of container width
        var minWidth = containerWidth * 0.2;
        var maxWidth = containerWidth * 0.8;
        var clampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
        
        leftPanel.style.flex = '0 0 ' + clampedWidth + 'px';
      }
    }
    
    /**
     * Handle end of resize drag
     */
    function endResize() {
      if (isResizing) {
        isResizing = false;
        resizer.classList.remove('dragging');
        overlay.classList.remove('active');
        document.body.style.userSelect = '';
      }
    }
    
    // Mouse events on resizer
    resizer.addEventListener('mousedown', startResize);
    resizer.addEventListener('touchstart', startResize);
    
    // Movement events on document and overlay
    document.addEventListener('mousemove', handleMove);
    document.addEventListener('mouseup', endResize);
    document.addEventListener('touchmove', handleMove, { passive: false });
    document.addEventListener('touchend', endResize);
    
    // Also listen on overlay for smooth dragging over iframe
    overlay.addEventListener('mousemove', handleMove);
    overlay.addEventListener('mouseup', endResize);
    overlay.addEventListener('touchmove', handleMove, { passive: false });
    overlay.addEventListener('touchend', endResize);
  }
  
  /* ==========================================================================
     INITIALIZATION
     ========================================================================== */
  
  /**
   * Initialize split panel functionality
   * Called automatically on DOMContentLoaded
   */
  function initSplitPanel() {
    // Initialize the resizer
    initResizer();
    
    // If CircuitJSUtils is available, set up scroll detection on the content iframe
    // (The content iframe handles its own scroll detection via circuitjs-utils.js)
    
    // Wait for CircuitJS iframe to load, then load first circuit
    var circuitIframe = document.querySelector('.split-left iframe');
    if (circuitIframe) {
      circuitIframe.addEventListener('load', function() {
        // Small delay for CircuitJS to fully initialize
        setTimeout(function() {
          // Check if content iframe has circuit sections
          var contentIframe = document.querySelector('.split-right iframe');
          if (contentIframe && contentIframe.contentWindow) {
            try {
              var sections = contentIframe.contentDocument.querySelectorAll('.circuit-section[data-circuit]');
              if (sections.length > 0) {
                var firstSection = sections[0];
                var circuitUrl = firstSection.getAttribute('data-circuit');
                var sectionName = firstSection.getAttribute('data-name') || firstSection.id;
                
                // Load first circuit using CircuitJSUtils if available
                if (typeof CircuitJSUtils !== 'undefined') {
                  CircuitJSUtils.loadCircuitUrl(circuitUrl, sectionName);
                }
              }
            } catch (e) {
              // Cross-origin iframe, content will handle its own loading
            }
          }
        }, 500);
      });
    }
  }
  
  // Auto-initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSplitPanel);
  } else {
    initSplitPanel();
  }
  
})();
