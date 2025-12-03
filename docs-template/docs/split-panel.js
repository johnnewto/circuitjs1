/**
 * Split Panel Layout - Reusable JavaScript for CircuitJS documentation pages
 * 
 * This script provides:
 * - Toast notifications for circuit loading
 * - Scroll-based circuit switching
 * - Draggable panel resizer
 * 
 * Usage:
 * 1. Include this script in your page
 * 2. Use class="circuit-section" with data-circuit and data-name attributes
 * 3. Use the split-container, split-left, split-resizer, split-right classes
 */

// Track if current section failed to load (resets on successful section change)
let currentSectionFailed = false;

// Toast notification system
function showToast(message, type = 'loading') {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }
  
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  const icon = type === 'loading' 
    ? '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="12"/></svg>'
    : type === 'success'
    ? '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
    : '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
  
  toast.innerHTML = `${icon}<span>${message}</span>`;
  container.appendChild(toast);
  
  // Trigger animation
  requestAnimationFrame(() => {
    toast.classList.add('show');
  });
  
  // Auto-hide after 2 seconds
  setTimeout(() => {
    toast.classList.remove('show');
    toast.classList.add('hide');
    setTimeout(() => toast.remove(), 300);
  }, 2000);
  
  return toast;
}

// Function to collect circuit URLs and names from data-circuit attributes on section elements
function getCircuitSections() {
  const sections = {};
  document.querySelectorAll('.circuit-section[data-circuit]').forEach(el => {
    sections[el.id] = {
      url: el.dataset.circuit,
      name: el.dataset.name || el.id
    };
  });
  return sections;
}

// Extract CTZ data from a circuit URL
function extractCTZ(url) {
  const match = url.match(/[?&]ctz=([^&]+)/);
  return match ? match[1] : null;
}

// Throttle function to limit scroll event frequency
function throttle(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

// Function to update circuit based on visible section (without iframe reload)
function updateCircuitOnScroll() {
  const iframe = document.querySelector('.split-left iframe');
  if (!iframe) return;
  
  const circuitSections = getCircuitSections();
  const scrollContainer = document.querySelector('.split-right');
  if (!scrollContainer) return;
  
  const containerTop = scrollContainer.getBoundingClientRect().top;
  
  // Find which section is currently most visible
  let currentSection = null;
  let minDistance = Infinity;
  
  for (const sectionId in circuitSections) {
    const element = document.getElementById(sectionId);
    if (!element) continue;
    
    const rect = element.getBoundingClientRect();
    const distance = Math.abs(rect.top - containerTop - 100); // 100px threshold
    
    if (distance < minDistance && rect.top < window.innerHeight * 0.6) {
      minDistance = distance;
      currentSection = sectionId;
    }
  }
  
  // Update circuit if section changed
  if (currentSection && iframe.dataset.currentSection !== currentSection) {
    // Reset failure flag when moving to a new section
    currentSectionFailed = false;
    iframe.dataset.currentSection = currentSection;
    const sectionData = circuitSections[currentSection];
    const circuitUrl = sectionData.url;
    const sectionName = sectionData.name;
    
    // Try to use JS API to load circuit without iframe reload
    const iframeWindow = iframe.contentWindow;
    if (iframeWindow && iframeWindow.CircuitJS1) {
      const ctz = extractCTZ(circuitUrl);
      if (ctz) {
        // Use new importCircuitFromCTZ API - no iframe reload needed!
        showToast(`Loading: ${sectionName}`, 'loading');
        iframeWindow.CircuitJS1.importCircuitFromCTZ(ctz, false);
      } else {
        // For startCircuit= URLs, extract filename and use importCircuitFromText with fetch
        const startCircuitMatch = circuitUrl.match(/[?&]startCircuit=([^&]+)/);
        if (startCircuitMatch) {
          const circuitFile = startCircuitMatch[1];
          showToast(`Loading: ${sectionName}`, 'loading');
          // Fetch the circuit file and load it
          fetch('../circuitjs1/circuits/' + circuitFile)
            .then(response => {
              if (!response.ok) {
                console.warn('Circuit file not found:', circuitFile);
                currentSectionFailed = true;
                showToast(`Not found: ${sectionName}`, 'error');
                return null;
              }
              return response.text();
            })
            .then(text => {
              if (text && iframeWindow.CircuitJS1) {
                iframeWindow.CircuitJS1.importCircuitFromText(text, false);
              }
            })
            .catch(err => {
              console.warn('Failed to load circuit:', circuitFile, err);
              currentSectionFailed = true;
              showToast(`Failed: ${sectionName}`, 'error');
            });
        } else {
          // Fallback: reload iframe for other URL types
          iframe.src = circuitUrl;
        }
      }
    } else {
      // CircuitJS not loaded yet, use iframe src change
      iframe.src = circuitUrl;
    }
  } else if (currentSection && currentSectionFailed) {
    // Same section and it failed - don't retry
    return;
  }
}

// Resizer drag functionality
function initResizer() {
  const container = document.querySelector('.split-container');
  const leftPanel = document.querySelector('.split-left');
  const resizer = document.querySelector('.split-resizer');
  
  if (!resizer || !leftPanel || !container) return;
  
  // Create overlay to prevent iframe from capturing mouse events
  const overlay = document.createElement('div');
  overlay.className = 'resize-overlay';
  document.body.appendChild(overlay);
  
  let isResizing = false;
  
  // Check if we're in mobile/vertical mode
  const isVerticalMode = () => window.innerWidth <= 768;
  
  resizer.addEventListener('mousedown', (e) => {
    isResizing = true;
    resizer.classList.add('dragging');
    overlay.classList.add('active');
    document.body.style.userSelect = 'none';
    e.preventDefault();
  });
  
  // Touch support for mobile
  resizer.addEventListener('touchstart', (e) => {
    isResizing = true;
    resizer.classList.add('dragging');
    overlay.classList.add('active');
    document.body.style.userSelect = 'none';
    e.preventDefault();
  });
  
  // Listen on overlay too for smooth dragging over iframe
  const handleMouseMove = (e) => {
    if (!isResizing) return;
    
    const containerRect = container.getBoundingClientRect();
    
    if (isVerticalMode()) {
      // Vertical resizing (top/bottom)
      const clientY = e.touches ? e.touches[0].clientY : e.clientY;
      const newHeight = clientY - containerRect.top;
      const containerHeight = containerRect.height;
      
      // Constrain between 20% and 70% of container height
      const minHeight = containerHeight * 0.2;
      const maxHeight = containerHeight * 0.7;
      const clampedHeight = Math.max(minHeight, Math.min(maxHeight, newHeight));
      
      leftPanel.style.flex = `0 0 ${clampedHeight}px`;
    } else {
      // Horizontal resizing (left/right)
      const clientX = e.touches ? e.touches[0].clientX : e.clientX;
      const newWidth = clientX - containerRect.left;
      const containerWidth = containerRect.width;
      
      // Constrain between 20% and 80% of container
      const minWidth = containerWidth * 0.2;
      const maxWidth = containerWidth * 0.8;
      const clampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
      
      leftPanel.style.flex = `0 0 ${clampedWidth}px`;
    }
  };
  
  const handleMouseUp = () => {
    if (isResizing) {
      isResizing = false;
      resizer.classList.remove('dragging');
      overlay.classList.remove('active');
      document.body.style.userSelect = '';
    }
  };
  
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
  document.addEventListener('touchmove', handleMouseMove, { passive: false });
  document.addEventListener('touchend', handleMouseUp);
  overlay.addEventListener('mousemove', handleMouseMove);
  overlay.addEventListener('mouseup', handleMouseUp);
  overlay.addEventListener('touchmove', handleMouseMove, { passive: false });
  overlay.addEventListener('touchend', handleMouseUp);
}

// Initialize split panel on page load
function initSplitPanel() {
  const scrollContainer = document.querySelector('.split-right');
  const iframe = document.querySelector('.split-left iframe');
  
  // Initialize the resizer
  initResizer();
  
  if (scrollContainer) {
    // Add scroll listener with throttling
    scrollContainer.addEventListener('scroll', throttle(updateCircuitOnScroll, 200));
  }
  
  // Wait for iframe to load before setting initial circuit
  if (iframe) {
    iframe.addEventListener('load', function() {
      // Small delay to ensure CircuitJS1 is fully initialized
      setTimeout(function() {
        // Force load the first section's circuit
        const circuitSections = getCircuitSections();
        const firstSectionId = Object.keys(circuitSections)[0];
        if (firstSectionId) {
          const sectionData = circuitSections[firstSectionId];
          const circuitUrl = sectionData.url;
          const sectionName = sectionData.name;
          const iframeWindow = iframe.contentWindow;
          if (iframeWindow && iframeWindow.CircuitJS1) {
            const ctz = extractCTZ(circuitUrl);
            if (ctz) {
              showToast(`Loading: ${sectionName}`, 'loading');
              iframeWindow.CircuitJS1.importCircuitFromCTZ(ctz, false);
              iframe.dataset.currentSection = firstSectionId;
            }
          }
        }
      }, 500);
    });
  }
}

// Auto-initialize when DOM is ready
document.addEventListener('DOMContentLoaded', initSplitPanel);
