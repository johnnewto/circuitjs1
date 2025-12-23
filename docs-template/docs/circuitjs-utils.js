/**
 * CircuitJS Utilities - Shared JavaScript for CircuitJS documentation pages
 * 
 * This module provides common functionality for interacting with CircuitJS:
 * - Finding CircuitJS instance across different contexts (iframe, parent, etc.)
 * - Toast notifications
 * - Slider animations
 * - Real-time data tables
 * - Circuit loading from data attributes
 * 
 * Usage contexts:
 * 1. Split-panel page (money-first-principles.qmd) - CircuitJS in left iframe
 * 2. Embedded content (money-content.qmd in IframeViewerDialog) - CircuitJS in parent window
 * 
 * @author CircuitJS1 Team
 */

(function(global) {
  'use strict';

  // ============================================================================
  // CIRCUITJS DETECTION
  // ============================================================================

  // Cache for CircuitJS instance once found
  var cachedCircuitJS = null;

  /**
   * Find the CircuitJS1 instance across different window contexts.
   * Searches in order: parent window, grandparent, sibling iframes, local iframe, current window.
   * Caches result after first successful find.
   * 
   * @returns {Object|null} CircuitJS1 API object or null if not found
   */
  function getCircuitJS() {
    // Return cached instance if available and still valid
    if (cachedCircuitJS) {
      try {
        // Quick validation that the cached reference is still valid
        if (cachedCircuitJS.getTime !== undefined) {
            console.log("Using cached CircuitJS instance");
          return cachedCircuitJS;
        }
      } catch (e) {
        // Reference became invalid, clear cache
        cachedCircuitJS = null;
      }
    }

    // 1. Check parent window (when embedded in IframeViewerDialog)
    if (window.parent && window.parent !== window) {
      if (window.parent.CircuitJS1) {
        cachedCircuitJS = window.parent.CircuitJS1;
        console.log("Using CircuitJS instance from parent window");
        return cachedCircuitJS;
      }
      // Check grandparent (deeply nested iframes)
      if (window.parent.parent && window.parent.parent !== window.parent) {
        if (window.parent.parent.CircuitJS1) {
            cachedCircuitJS = window.parent.parent.CircuitJS1;
            console.log("Using CircuitJS instance from grandparent window");
            return cachedCircuitJS;
        }
      }
      // Check sibling iframes in parent
      try {
        var parentIframes = window.parent.document.querySelectorAll('iframe');
        for (var i = 0; i < parentIframes.length; i++) {
          try {
            if (parentIframes[i].contentWindow && parentIframes[i].contentWindow.CircuitJS1) {
                cachedCircuitJS = parentIframes[i].contentWindow.CircuitJS1;
                console.log("Using CircuitJS instance from sibling iframe");
                return cachedCircuitJS;
            }
          } catch (e) { /* cross-origin, skip */ }
        }
      } catch (e) { /* cross-origin, skip */ }
    }

    // 2. Check local iframe (split-panel layout with CircuitJS on left)
    var localIframe = document.querySelector('.split-left iframe');
    if (localIframe && localIframe.contentWindow && localIframe.contentWindow.CircuitJS1) {
        cachedCircuitJS = localIframe.contentWindow.CircuitJS1;
        console.log("Using CircuitJS instance from local iframe");
        return cachedCircuitJS;
    }

    // 3. Check current window (CircuitJS loaded directly)
    if (window.CircuitJS1) {
        cachedCircuitJS = window.CircuitJS1;
        return cachedCircuitJS;
    }

    return null;
  }

  // ============================================================================
  // TOAST NOTIFICATIONS
  // ============================================================================

  /**
   * Show a toast notification message.
   * 
   * @param {string} message - The message to display
   * @param {string} type - 'loading', 'success', or 'error'
   * @returns {HTMLElement} The toast element
   */
  function showToast(message, type) {
    type = type || 'loading';
    
    // Create container if needed
    var container = document.querySelector('.toast-container');
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    // Create toast element
    var toast = document.createElement('div');
    toast.className = 'toast ' + type;

    // Icon SVGs
    var icons = {
      loading: '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="12"/></svg>',
      success: '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>',
      error: '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'
    };

    toast.innerHTML = (icons[type] || icons.loading) + '<span>' + message + '</span>';
    container.appendChild(toast);

    // Animate in
    requestAnimationFrame(function() {
      toast.classList.add('show');
    });

    // Auto-hide after 2 seconds
    setTimeout(function() {
      toast.classList.remove('show');
      toast.classList.add('hide');
      setTimeout(function() { toast.remove(); }, 300);
    }, 2000);

    return toast;
  }

  // ============================================================================
  // SLIDER ANIMATIONS
  // ============================================================================

  /**
   * Easing function for smooth animations.
   * @param {number} t - Progress from 0 to 1
   * @returns {number} Eased value
   */
  function easeInOut(t) {
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }

  /**
   * Toggle a slider value by a percentage, then return to original.
   * Creates a smooth animation up and then back down.
   * 
   * @param {string} sliderName - Name of the slider to toggle
   * @param {number} changePercent - Percentage change (e.g., 0.5 for +50%)
   * @param {number} durationMs - Animation duration in milliseconds
   */
  function toggleSlider(sliderName, changePercent, durationMs) {
    var sim = getCircuitJS();
    if (!sim) {
      showToast('CircuitJS not ready', 'error');
      return;
    }

    var currentValue = sim.getSliderValue(sliderName);
    if (isNaN(currentValue)) {
      showToast('Slider not found: ' + sliderName, 'error');
      return;
    }

    var startValue = currentValue;
    var targetValue = currentValue * (1 + changePercent);
    var startTime = performance.now();

    showToast('Adjusting ' + sliderName + '...', 'loading');

    // Animate to target
    function animateUp(currentTime) {
      var elapsed = currentTime - startTime;
      var progress = Math.min(elapsed / durationMs, 1);
      sim.setSliderValue(sliderName, startValue + (targetValue - startValue) * easeInOut(progress));

      if (progress < 1) {
        requestAnimationFrame(animateUp);
      } else {
        // Pause, then animate back
        setTimeout(function() {
          var backStart = performance.now();
          function animateDown(t) {
            var elapsed = t - backStart;
            var progress = Math.min(elapsed / durationMs, 1);
            sim.setSliderValue(sliderName, targetValue + (startValue - targetValue) * easeInOut(progress));
            if (progress < 1) {
              requestAnimationFrame(animateDown);
            } else {
              showToast(sliderName + ' restored', 'success');
            }
          }
          requestAnimationFrame(animateDown);
        }, 500);
      }
    }
    requestAnimationFrame(animateUp);
  }

  // State tracking for cycleSlider
  var sliderCycleState = {};

  /**
   * Cycle a slider through a sequence of values on each button press.
   * Values persist until the next press. Optionally updates button text.
   * 
   * @param {string} sliderName - Name of the slider to cycle
   * @param {Array} values - Array of values to cycle through (e.g., [0, 5, 0, -5])
   * @param {number} durationMs - Animation duration in milliseconds (default 300)
   * @param {HTMLElement} button - Optional button element to update with current value
   */
  function cycleSlider(sliderName, values, durationMs, button) {
    durationMs = durationMs || 300;
    var sim = getCircuitJS();
    if (!sim) {
      showToast('CircuitJS not ready', 'error');
      return;
    }

    var currentValue = sim.getSliderValue(sliderName);
    if (isNaN(currentValue)) {
      showToast('Slider not found: ' + sliderName, 'error');
      return;
    }

    // Get or initialize cycle state for this slider
    if (sliderCycleState[sliderName] === undefined) {
      sliderCycleState[sliderName] = 0;
    }

    // Move to next value in cycle
    sliderCycleState[sliderName] = (sliderCycleState[sliderName] + 1) % values.length;
    var targetValue = values[sliderCycleState[sliderName]];

    // Update button text if provided
    if (button) {
      var valueSpan = button.querySelector('.slider-value');
      if (valueSpan) {
        var displayValue = (targetValue >= 0 ? '+' : '') + (targetValue * 100).toFixed(0) + '%';
        valueSpan.textContent = displayValue;
      }
    }

    var startValue = currentValue;
    var startTime = performance.now();

    showToast(sliderName + ' → ' + (targetValue * 100).toFixed(0) + '%', 'success');

    // Animate to target
    function animate(currentTime) {
      var elapsed = currentTime - startTime;
      var progress = Math.min(elapsed / durationMs, 1);
      sim.setSliderValue(sliderName, startValue + (targetValue - startValue) * easeInOut(progress));

      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    }
    requestAnimationFrame(animate);
  }

  /**
   * Animate a slider to a specific target value.
   * 
   * @param {string} sliderName - Name of the slider
   * @param {number} targetValue - Target value to animate to
   * @param {number} durationMs - Animation duration (default 500ms)
   */
  function animateSliderTo(sliderName, targetValue, durationMs) {
    durationMs = durationMs || 500;
    var sim = getCircuitJS();
    if (!sim) return;

    var startValue = sim.getSliderValue(sliderName);
    var startTime = performance.now();

    function animate(currentTime) {
      var elapsed = currentTime - startTime;
      var progress = Math.min(elapsed / durationMs, 1);
      sim.setSliderValue(sliderName, startValue + (targetValue - startValue) * easeInOut(progress));
      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    }
    requestAnimationFrame(animate);
  }

  // ============================================================================
  // REAL-TIME DATA TABLES
  // ============================================================================

  // Registry for active realtime tables
  var realtimeTables = {};

  /**
   * Format a numeric value for display.
   * @param {number} val - Value to format
   * @returns {string} Formatted string
   */
  function formatValue(val) {
    if (val === undefined || val === null || isNaN(val)) return '—';
    if (Math.abs(val) >= 1000) return val.toFixed(1);
    if (Math.abs(val) >= 1) return val.toFixed(2);
    return val.toFixed(3);
  }

  /**
   * Convert LaTeX-style subscript/superscript notation to HTML.
   * Handles patterns like: Name_{sub}^{super}, Name_{sub}, Name^{super}
   * @param {string} name - Label name with LaTeX notation
   * @returns {string} HTML formatted string
   */
  function formatLabelAsHTML(name) {
    if (!name) return '';
    // Convert _{...} to <sub>...</sub>
    var result = name.replace(/\_\{([^}]+)\}/g, '<sub>$1</sub>');
    // Convert ^{...} to <sup>...</sup>
    result = result.replace(/\^\{([^}]+)\}/g, '<sup>$1</sup>');
    return result;
  }

  /**
   * Initialize a real-time data table that updates from CircuitJS.
   * Caches the CircuitJS reference locally to avoid repeated lookups.
   * 
   * @param {string} tableId - ID of the table element
   * @param {Array} labels - Array of label names to display (empty = all)
   * @param {number} updateInterval - Update interval in ms (default 100)
   * @returns {Function} Stop function to clear the interval
   */
  function initRealtimeTable(tableId, labels, updateInterval) {
    labels = labels || [];
    updateInterval = updateInterval || 100;

    var table = document.getElementById(tableId);
    if (!table) {
      console.warn('Realtime table not found:', tableId);
      return function() {};
    }

    // Cache sim reference locally - only look up once
    var sim = null;
    var retryCount = 0;
    var maxRetries = 50; // Stop trying after ~5 seconds

    function updateTable() {
      // Get sim reference once, then reuse
      if (!sim) {
        sim = getCircuitJS();
        if (!sim) {
          retryCount++;
          if (retryCount >= maxRetries) {
            console.warn('CircuitJS not found after ' + maxRetries + ' attempts, stopping table updates');
            return;
          }
          return; // Try again next interval
        }
      }

      var tbody = table.querySelector('tbody');
      if (!tbody) return;

      // Get node names
      var nodeNames = labels;
      if (nodeNames.length === 0 && sim.getLabeledNodeNames) {
        var jsArray = sim.getLabeledNodeNames();
        nodeNames = [];
        for (var i = 0; i < jsArray.length; i++) {
          nodeNames.push(jsArray[i]);
        }
      }

      // Update table rows
      tbody.innerHTML = '';
      nodeNames.forEach(function(name) {
        var value = sim.getLabeledNodeValue ? sim.getLabeledNodeValue(name) : 
                    sim.getNodeVoltage ? sim.getNodeVoltage(name) : 0;
        var row = document.createElement('tr');
        row.innerHTML = '<td class="rt-label">' + formatLabelAsHTML(name) + '</td>' +
                        '<td class="rt-value">' + formatValue(value) + '</td>';
        tbody.appendChild(row);
      });
    }

    var intervalId = setInterval(updateTable, updateInterval);
    setTimeout(updateTable, 1000); // Initial update after CircuitJS loads

    return function() { clearInterval(intervalId); };
  }

  /**
   * Start a realtime table (stops existing one if running).
   * 
   * @param {string} tableId - ID of the table element
   * @param {Array} labels - Array of label names to display
   * @param {number} updateInterval - Update interval in ms
   */
  function startRealtimeTable(tableId, labels, updateInterval) {
    if (realtimeTables[tableId]) {
      realtimeTables[tableId](); // Stop existing
    }
    realtimeTables[tableId] = initRealtimeTable(tableId, labels, updateInterval);
  }

  // ============================================================================
  // CIRCUIT SECTION LOADING
  // ============================================================================

  // Track current section to avoid reloading
  var currentSection = null;
  var currentSectionFailed = false;

  /**
   * Extract CTZ data from a circuit URL.
   * @param {string} url - Circuit URL
   * @returns {string|null} CTZ data or null
   */
  function extractCTZ(url) {
    var match = url.match(/[?&]ctz=([^&]+)/);
    return match ? match[1] : null;
  }

  /**
   * Load a circuit for a section based on its data-circuit attribute.
   * 
   * @param {string} sectionId - ID of the section element
   */
  function loadCircuitForSection(sectionId) {
    var section = document.getElementById(sectionId);
    if (!section) return;

    var circuitUrl = section.getAttribute('data-circuit');
    var sectionName = section.getAttribute('data-name') || sectionId;
    if (!circuitUrl) return;

    var sim = getCircuitJS();
    if (!sim) {
      console.warn('CircuitJS not available for loading circuit');
      return;
    }

    // Try CTZ data first
    var ctz = extractCTZ(circuitUrl);
    if (ctz) {
      showToast('Loading: ' + sectionName, 'loading');
      sim.importCircuitFromCTZ(ctz, false);
      return;
    }

    // Try startCircuit filename
    var startCircuitMatch = circuitUrl.match(/[?&]startCircuit=([^&]+)/);
    if (startCircuitMatch) {
      var circuitFile = startCircuitMatch[1];
      showToast('Loading: ' + sectionName, 'loading');

      // Determine base URL for circuit files
      var baseUrl = '';
      if (window.parent && window.parent.location && window.parent !== window) {
        baseUrl = window.parent.location.href.replace(/[^/]*$/, '');
      }

      fetch(baseUrl + 'circuitjs1/circuits/' + circuitFile)
        .then(function(response) {
          if (!response.ok) {
            currentSectionFailed = true;
            showToast('Not found: ' + circuitFile, 'error');
            return null;
          }
          return response.text();
        })
        .then(function(text) {
          if (text && sim.importCircuitFromText) {
            sim.importCircuitFromText(text, false);
          }
        })
        .catch(function(err) {
          currentSectionFailed = true;
          showToast('Failed: ' + circuitFile, 'error');
        });
    }
  }

  /**
   * Find the section that is currently most visible in the viewport.
   * Returns the section whose top is closest to the viewport center.
   * 
   * @returns {HTMLElement|null} The most visible section element
   */
  function findVisibleSection() {
    var sections = document.querySelectorAll('.circuit-section[data-circuit]');
    if (sections.length === 0) return null;

    var viewportHeight = window.innerHeight;
    var targetY = viewportHeight * 0.3; // Look for section near top third of viewport
    var bestSection = null;
    var bestDistance = Infinity;

    sections.forEach(function(section) {
      var rect = section.getBoundingClientRect();
      // Section is considered if any part is visible
      if (rect.bottom > 0 && rect.top < viewportHeight) {
        // Distance from section top to our target position
        var distance = Math.abs(rect.top - targetY);
        if (distance < bestDistance) {
          bestDistance = distance;
          bestSection = section;
        }
      }
    });

    return bestSection;
  }

  /**
   * Check current scroll position and load circuit if section changed.
   */
  function checkAndLoadSection() {
    var visibleSection = findVisibleSection();
    if (visibleSection && visibleSection.id !== currentSection) {
      console.log('Section changed: "' + currentSection + '" -> "' + visibleSection.id + '"');
      currentSection = visibleSection.id;
      currentSectionFailed = false;
      loadCircuitForSection(visibleSection.id);
    }
  }

  /**
   * Set up scroll detection using throttled scroll handler.
   * Automatically loads circuits when sections scroll into view.
   */
  function setupScrollDetection() {
    var sections = document.querySelectorAll('.circuit-section[data-circuit]');
    console.log('setupScrollDetection: found ' + sections.length + ' circuit sections');
    if (sections.length === 0) return;

    // Log all section IDs found
    sections.forEach(function(s) {
      console.log('  - Section: id="' + s.id + '" data-name="' + s.getAttribute('data-name') + '"');
    });

    // Throttled scroll handler
    var scrollHandler = throttle(checkAndLoadSection, 150);

    // Listen for scroll on window (for embedded content) or find scroll container
    window.addEventListener('scroll', scrollHandler, { passive: true });

    // Also check on resize
    window.addEventListener('resize', throttle(checkAndLoadSection, 250), { passive: true });

    // Load first section after delay
    setTimeout(function() {
      if (sections.length > 0 && !currentSection) {
        console.log('Loading first section: ' + sections[0].id);
        currentSection = sections[0].id;
        loadCircuitForSection(sections[0].id);
      }
    }, 1500);
  }

  // ============================================================================
  // SPLIT PANEL RESIZER
  // ============================================================================

  /**
   * Initialize the draggable resizer for split-panel layouts.
   */
  function initResizer() {
    var container = document.querySelector('.split-container');
    var leftPanel = document.querySelector('.split-left');
    var resizer = document.querySelector('.split-resizer');

    if (!resizer || !leftPanel || !container) return;

    // Create overlay to capture mouse during resize
    var overlay = document.createElement('div');
    overlay.className = 'resize-overlay';
    document.body.appendChild(overlay);

    var isResizing = false;
    var isVerticalMode = function() { return window.innerWidth <= 768; };

    function startResize(e) {
      isResizing = true;
      resizer.classList.add('dragging');
      overlay.classList.add('active');
      document.body.style.userSelect = 'none';
      e.preventDefault();
    }

    function doResize(e) {
      if (!isResizing) return;

      var containerRect = container.getBoundingClientRect();
      var clientPos = e.touches ? e.touches[0] : e;

      if (isVerticalMode()) {
        // Vertical resizing
        var newHeight = clientPos.clientY - containerRect.top;
        var minHeight = containerRect.height * 0.2;
        var maxHeight = containerRect.height * 0.7;
        leftPanel.style.flex = '0 0 ' + Math.max(minHeight, Math.min(maxHeight, newHeight)) + 'px';
      } else {
        // Horizontal resizing
        var newWidth = clientPos.clientX - containerRect.left;
        var minWidth = containerRect.width * 0.2;
        var maxWidth = containerRect.width * 0.8;
        leftPanel.style.flex = '0 0 ' + Math.max(minWidth, Math.min(maxWidth, newWidth)) + 'px';
      }
    }

    function endResize() {
      if (isResizing) {
        isResizing = false;
        resizer.classList.remove('dragging');
        overlay.classList.remove('active');
        document.body.style.userSelect = '';
      }
    }

    // Event listeners
    resizer.addEventListener('mousedown', startResize);
    resizer.addEventListener('touchstart', startResize);
    document.addEventListener('mousemove', doResize);
    document.addEventListener('mouseup', endResize);
    document.addEventListener('touchmove', doResize, { passive: false });
    document.addEventListener('touchend', endResize);
    overlay.addEventListener('mousemove', doResize);
    overlay.addEventListener('mouseup', endResize);
  }

  /**
   * Throttle function to limit execution frequency.
   * @param {Function} func - Function to throttle
   * @param {number} wait - Wait time in ms
   * @returns {Function} Throttled function
   */
  function throttle(func, wait) {
    var timeout;
    return function() {
      var args = arguments;
      var later = function() {
        clearTimeout(timeout);
        func.apply(null, args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  /**
   * Initialize the split-panel layout (resizer + scroll detection).
   * Call this for main split-panel pages.
   */
  function initSplitPanel() {
    initResizer();

    var scrollContainer = document.querySelector('.split-right');
    if (scrollContainer && !scrollContainer.querySelector('iframe')) {
      // Only add scroll listener if right panel has direct content (not iframe)
      scrollContainer.addEventListener('scroll', throttle(function() {
        // Use the iframe-based circuit update for split-panel pages
        updateCircuitFromSplitPanel();
      }, 200));
    }

    // Wait for iframe to load
    var iframe = document.querySelector('.split-left iframe');
    if (iframe) {
      iframe.addEventListener('load', function() {
        setTimeout(function() {
          // Load first section's circuit
          var sections = document.querySelectorAll('.circuit-section[data-circuit]');
          if (sections.length > 0) {
            var firstSection = sections[0];
            var ctz = extractCTZ(firstSection.getAttribute('data-circuit'));
            if (ctz && iframe.contentWindow && iframe.contentWindow.CircuitJS1) {
              showToast('Loading: ' + (firstSection.getAttribute('data-name') || firstSection.id), 'loading');
              iframe.contentWindow.CircuitJS1.importCircuitFromCTZ(ctz, false);
              iframe.dataset.currentSection = firstSection.id;
            }
          }
        }, 500);
      });
    }
  }

  /**
   * Update circuit based on scroll position (for split-panel with local iframe).
   */
  function updateCircuitFromSplitPanel() {
    var iframe = document.querySelector('.split-left iframe');
    if (!iframe) return;

    var scrollContainer = document.querySelector('.split-right');
    if (!scrollContainer) return;

    var sections = document.querySelectorAll('.circuit-section[data-circuit]');
    var containerTop = scrollContainer.getBoundingClientRect().top;

    // Find most visible section
    var bestSection = null;
    var minDistance = Infinity;

    sections.forEach(function(section) {
      var rect = section.getBoundingClientRect();
      var distance = Math.abs(rect.top - containerTop - 100);
      if (distance < minDistance && rect.top < window.innerHeight * 0.6) {
        minDistance = distance;
        bestSection = section;
      }
    });

    // Update circuit if section changed
    if (bestSection && iframe.dataset.currentSection !== bestSection.id) {
      currentSectionFailed = false;
      iframe.dataset.currentSection = bestSection.id;

      var circuitUrl = bestSection.getAttribute('data-circuit');
      var sectionName = bestSection.getAttribute('data-name') || bestSection.id;
      var iframeWindow = iframe.contentWindow;

      if (iframeWindow && iframeWindow.CircuitJS1) {
        var ctz = extractCTZ(circuitUrl);
        if (ctz) {
          showToast('Loading: ' + sectionName, 'loading');
          iframeWindow.CircuitJS1.importCircuitFromCTZ(ctz, false);
        } else {
          var startCircuitMatch = circuitUrl.match(/[?&]startCircuit=([^&]+)/);
          if (startCircuitMatch) {
            showToast('Loading: ' + sectionName, 'loading');
            fetch('../circuitjs1/circuits/' + startCircuitMatch[1])
              .then(function(r) { return r.ok ? r.text() : null; })
              .then(function(text) {
                if (text && iframeWindow.CircuitJS1) {
                  iframeWindow.CircuitJS1.importCircuitFromText(text, false);
                }
              })
              .catch(function() {
                currentSectionFailed = true;
                showToast('Failed: ' + sectionName, 'error');
              });
          }
        }
      }
    }
  }

  // ============================================================================
  // EXPORTS
  // ============================================================================

  // Create CircuitJSUtils namespace object
  var CircuitJSUtils = {
    getCircuitJS: getCircuitJS,
    showToast: showToast,
    toggleSlider: toggleSlider,
    cycleSlider: cycleSlider,
    animateSliderTo: animateSliderTo,
    initRealtimeTable: initRealtimeTable,
    startRealtimeTable: startRealtimeTable,
    loadCircuitForSection: loadCircuitForSection,
    initScrollDetection: setupScrollDetection,
    setupScrollDetection: setupScrollDetection,
    initSplitPanel: initSplitPanel,
    initResizer: initResizer,
    sendTOCToParent: sendTOCToParent,
    initParentTOCListener: initParentTOCListener,
    toggleMenuPanel: toggleMenuPanel,
    openMenuPanel: openMenuPanel,
    closeMenuPanel: closeMenuPanel,
    toggleFullscreen: toggleFullscreen
  };

  // ============================================================================
  // SLIDE-OUT MENU & TOC COMMUNICATION
  // ============================================================================

  /**
   * Toggle fullscreen mode for the page.
   * Used by fullscreen button in parent page.
   */
  function toggleFullscreen() {
    var btn = document.querySelector('.fullscreen-btn');
    
    if (!document.fullscreenElement && !document.webkitFullscreenElement) {
      // Enter fullscreen
      var elem = document.documentElement;
      if (elem.requestFullscreen) {
        elem.requestFullscreen();
      } else if (elem.webkitRequestFullscreen) {
        elem.webkitRequestFullscreen();
      }
      if (btn) btn.classList.add('is-fullscreen');
    } else {
      // Exit fullscreen
      if (document.exitFullscreen) {
        document.exitFullscreen();
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      }
      if (btn) btn.classList.remove('is-fullscreen');
    }
  }

  // Listen for fullscreen changes (e.g., user pressing Escape)
  document.addEventListener('fullscreenchange', function() {
    var btn = document.querySelector('.fullscreen-btn');
    if (btn) {
      if (document.fullscreenElement) {
        btn.classList.add('is-fullscreen');
      } else {
        btn.classList.remove('is-fullscreen');
      }
    }
  });

  document.addEventListener('webkitfullscreenchange', function() {
    var btn = document.querySelector('.fullscreen-btn');
    if (btn) {
      if (document.webkitFullscreenElement) {
        btn.classList.add('is-fullscreen');
      } else {
        btn.classList.remove('is-fullscreen');
      }
    }
  });

  /**
   * Toggle the slide-out menu panel.
   * Used by menu button in parent page.
   */
  function toggleMenuPanel() {
    var panel = document.querySelector('.menu-panel');
    var overlay = document.querySelector('.menu-panel-overlay');
    if (panel) panel.classList.toggle('open');
    if (overlay) overlay.classList.toggle('open');
  }

  /**
   * Open the slide-out menu panel.
   * Used by hover trigger.
   */
  function openMenuPanel() {
    var panel = document.querySelector('.menu-panel');
    var overlay = document.querySelector('.menu-panel-overlay');
    if (panel) panel.classList.add('open');
    if (overlay) overlay.classList.add('open');
  }

  /**
   * Close the slide-out menu panel.
   */
  function closeMenuPanel() {
    var panel = document.querySelector('.menu-panel');
    var overlay = document.querySelector('.menu-panel-overlay');
    if (panel) panel.classList.remove('open');
    if (overlay) overlay.classList.remove('open');
  }

  /**
   * Send TOC data to parent frame (called from content iframe).
   * Builds TOC from h1, h2, h3 headings and posts to parent.
   */
  function sendTOCToParent() {
    if (window.parent === window) return; // Not in iframe

    // Build TOC from headings
    var headings = document.querySelectorAll('h1, h2, h3');
    var tocData = [];

    headings.forEach(function(heading, index) {
      // Ensure heading has an ID for linking
      if (!heading.id) {
        heading.id = 'heading-' + index;
      }
      tocData.push({
        id: heading.id,
        text: heading.textContent,
        level: heading.tagName.toLowerCase()
      });
    });

    // Send to parent
    window.parent.postMessage({
      type: 'toc-data',
      toc: tocData
    }, '*');
  }

  /**
   * Build TOC list from data received via postMessage.
   * @param {Array} tocData - Array of {id, text, level} objects
   */
  function buildTOCFromData(tocData) {
    var tocContainer = document.getElementById('toc-container');
    if (!tocContainer) return;

    if (!tocData || tocData.length === 0) {
      tocContainer.innerHTML = '<p style="color: #999; font-size: 13px;">No sections found</p>';
      return;
    }

    var tocList = document.createElement('ul');
    tocList.className = 'toc-list';

    tocData.forEach(function(item) {
      var li = document.createElement('li');
      li.className = 'toc-item toc-' + item.level;

      var link = document.createElement('a');
      link.textContent = item.text;
      link.href = '#' + item.id;
      link.addEventListener('click', function(e) {
        e.preventDefault();
        // Send scroll request to content iframe
        var contentIframe = document.querySelector('.split-right iframe');
        if (contentIframe && contentIframe.contentWindow) {
          contentIframe.contentWindow.postMessage({
            type: 'scroll-to',
            id: item.id
          }, '*');
        }
        // Close the menu panel
        toggleMenuPanel();
      });

      li.appendChild(link);
      tocList.appendChild(li);
    });

    tocContainer.innerHTML = '';
    tocContainer.appendChild(tocList);
  }

  /**
   * Initialize listener for TOC data from content iframe.
   * Call this in parent page.
   */
  function initParentTOCListener() {
    window.addEventListener('message', function(event) {
      if (event.data && event.data.type === 'toc-data') {
        buildTOCFromData(event.data.toc);
      }
    });
  }

  /**
   * Initialize listener for scroll requests from parent.
   * Call this in content iframe.
   */
  function initChildScrollListener() {
    window.addEventListener('message', function(event) {
      if (event.data && event.data.type === 'scroll-to') {
        var element = document.getElementById(event.data.id);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth' });
        }
      }
    });
  }

  // Export namespace to global scope
  global.CircuitJSUtils = CircuitJSUtils;

  // Also export individual functions for backward compatibility
  global.getCircuitJS = getCircuitJS;
  global.showToast = showToast;
  global.toggleSlider = toggleSlider;
  global.cycleSlider = cycleSlider;
  global.animateSliderTo = animateSliderTo;
  global.initRealtimeTable = initRealtimeTable;
  global.startRealtimeTable = startRealtimeTable;
  global.loadCircuitForSection = loadCircuitForSection;
  global.setupScrollDetection = setupScrollDetection;
  global.initSplitPanel = initSplitPanel;
  global.initResizer = initResizer;
  global.realtimeTables = realtimeTables;

  // Auto-initialize based on page type
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      // If we have a split-container, initialize split panel and TOC listener
      if (document.querySelector('.split-container')) {
        initSplitPanel();
        initParentTOCListener();
      }
      // If we have circuit-sections but no split-container, set up scroll detection
      // (embedded content case - also send TOC to parent and listen for scroll)
      else if (document.querySelector('.circuit-section[data-circuit]')) {
        setupScrollDetection();
        sendTOCToParent();
        initChildScrollListener();
      }
    });
  } else {
    if (document.querySelector('.split-container')) {
      initSplitPanel();
      initParentTOCListener();
    } else if (document.querySelector('.circuit-section[data-circuit]')) {
      setupScrollDetection();
      sendTOCToParent();
      initChildScrollListener();
    }
  }

})(window);
