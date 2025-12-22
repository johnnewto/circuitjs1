/**
 * Auto-embed and Split Layout Controller
 * 
 * Features:
 * 1. Auto-detects if page is in iframe → adds 'embedded' class to hide navbar
 * 2. Checks for ?split=true URL param → creates split layout with CircuitJS
 * 
 * Usage in _quarto.yml:
 *   - Direct: docs/user-guide.qmd
 *   - Split:  docs/user-guide.qmd?split=true
 */

(function() {
  'use strict';
  
  // Debug: confirm script is loaded
  console.log('[auto-embed] Script loaded at:', window.location.href);

  // 1. Detect if we're in an iframe and add 'embedded' class
  var isEmbedded = window.self !== window.top;
  console.log('[auto-embed] isEmbedded:', isEmbedded, 'location:', window.location.href);
  
  if (isEmbedded) {
    document.documentElement.classList.add('embedded');
    console.log('[auto-embed] Added embedded class, setting up link interceptor');
    
    // Intercept navigation links to update parent URL (maintain split mode)
    // Use capture phase to ensure we get the event first
    document.addEventListener('click', function(e) {
      console.log('[auto-embed] Click detected on:', e.target.tagName);
      var link = e.target.closest('a');
      if (!link) {
        console.log('[auto-embed] No link found');
        return;
      }
      
      var href = link.getAttribute('href');
      console.log('[auto-embed] Link href:', href);
      if (!href) return;
      
      // Skip anchors and special protocols
      if (href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('javascript:')) {
        console.log('[auto-embed] Skipping anchor/special link');
        return;
      }
      
      // Skip truly external links (different origin)
      if (href.startsWith('http')) {
        try {
          var linkUrl = new URL(href);
          if (linkUrl.origin !== window.location.origin) {
            console.log('[auto-embed] Skipping external link (different origin)');
            return;
          }
          // Same origin absolute URL - extract pathname
          href = linkUrl.pathname + linkUrl.search;
          console.log('[auto-embed] Same-origin absolute URL, using path:', href);
        } catch (e) {
          console.log('[auto-embed] Skipping malformed URL');
          return;
        }
      }
      
      // For internal .html or .qmd links, update parent URL with ?split=true
      if (href.endsWith('.html') || href.endsWith('.qmd') || href.includes('.html?') || href.includes('.qmd?')) {
        console.log('[auto-embed] Internal link detected, preventing default');
        e.preventDefault();
        e.stopPropagation();
        
        // Convert .qmd to .html (remove ?split=true for iframe src - iframe doesn't need it)
        var newHref = href.replace('.qmd', '.html');
        // Remove split=true from iframe URL since the iframe content shouldn't have it
        newHref = newHref.replace(/[?&]split=true/, '');
        
        // Resolve relative URL based on current iframe location
        var base = window.location.pathname.substring(0, window.location.pathname.lastIndexOf('/') + 1);
        var contentUrl = new URL(newHref, window.location.origin + base).href;
        console.log('[auto-embed] Updating content iframe to:', contentUrl);
        
        // Update only the content iframe, not the parent page
        try {
          // Find the content iframe in parent and update its src
          var contentIframe = window.parent.document.querySelector('#panel-right iframe');
          if (contentIframe) {
            contentIframe.src = contentUrl;
            // Update parent URL for bookmarking (using history API, no reload)
            var parentUrl = contentUrl.replace(/\?.*$/, '') + '?split=true';
            window.parent.history.pushState({}, '', parentUrl);
            console.log('[auto-embed] Updated content iframe and parent URL');
          } else {
            // Fallback: navigate parent (shouldn't happen in normal split mode)
            console.warn('[auto-embed] Content iframe not found, falling back to parent navigation');
            window.parent.location.href = contentUrl + (contentUrl.includes('?') ? '&' : '?') + 'split=true';
          }
        } catch (err) {
          // Fallback if cross-origin
          console.warn('[auto-embed] Could not update iframe, using postMessage', err);
          window.parent.postMessage({ type: 'navigate', url: contentUrl }, '*');
        }
        return false;
      }
    }, true); // Use capture phase
  }

  // 2. Check for split mode via URL parameter
  var urlParams = new URLSearchParams(window.location.search);
  var isSplitMode = urlParams.get('split') === 'true';

  if (!isSplitMode) return; // Exit if not split mode

  // Add split-mode class
  document.documentElement.classList.add('split-mode');

  // Listen for navigation messages from iframe (fallback for cross-origin)
  window.addEventListener('message', function(e) {
    if (e.data && e.data.type === 'navigate' && e.data.url) {
      window.location.href = e.data.url;
    }
  });

  // Wait for DOM
  function initSplitMode() {
    // Get the current page URL without the split parameter
    var contentUrl = window.location.pathname;
    
    // Measure navbar height
    var navbar = document.querySelector('#quarto-header, nav.navbar, header.navbar, .navbar');
    var navbarHeight = navbar ? navbar.getBoundingClientRect().height : 60;
    document.documentElement.style.setProperty('--navbar-height', navbarHeight + 'px');

    // Hide original content
    var mainContent = document.querySelector('#quarto-content, main.content, main');
    if (mainContent) {
      mainContent.style.display = 'none';
    }

    // Create split container
    var splitContainer = document.createElement('div');
    splitContainer.className = 'split-wrap';
    splitContainer.id = 'split';
    splitContainer.innerHTML = 
      '<div id="panel-left">' +
        '<iframe src="' + getCircuitJSUrl() + '" title="CircuitJS1 Electronic Circuit Simulator"></iframe>' +
      '</div>' +
      '<div id="panel-right">' +
        '<iframe src="' + contentUrl + '" title="Content"></iframe>' +
      '</div>';

    document.body.appendChild(splitContainer);

    // Load Split.js dynamically
    var script = document.createElement('script');
    script.src = 'https://unpkg.com/split.js/dist/split.min.js';
    script.onload = initSplitJS;
    document.head.appendChild(script);
  }

  function getCircuitJSUrl() {
    // Build absolute URL to circuitjs.html at site root
    // Works for both local dev (/) and GitHub Pages (/circuitjs1/)
    var pathname = window.location.pathname;
    
    // Find the site root by looking for known path segments
    // e.g., /circuitjs1/docs/money/ → /circuitjs1/
    // e.g., /docs/money/ → /
    var siteRoot = '/';
    var match = pathname.match(/^(\/[^\/]+\/)?docs\//);
    if (match && match[1]) {
      siteRoot = match[1]; // e.g., /circuitjs1/
    }
    
    return siteRoot + 'circuitjs.html?startCircuit=blank.txt&editable=true';
  }

  var splitInstance = null;
  var currentMode = null;

  function isMobile() {
    return window.innerWidth <= 768;
  }

  function initSplitJS() {
    var mobile = isMobile();
    var mode = mobile ? 'vertical' : 'horizontal';
    currentMode = mode;

    var container = document.getElementById('split');
    container.classList.toggle('vertical', mobile);

    splitInstance = Split(['#panel-left', '#panel-right'], {
      sizes: mobile ? [40, 60] : [60, 40],
      minSize: mobile ? [150, 150] : [250, 250],
      gutterSize: 8,
      direction: mode
    });

    // Handle resize
    var resizeTimer;
    window.addEventListener('resize', function() {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function() {
        var mobile = isMobile();
        var mode = mobile ? 'vertical' : 'horizontal';
        
        if (mode !== currentMode) {
          currentMode = mode;
          if (splitInstance) {
            splitInstance.destroy();
            var oldGutter = document.querySelector('.gutter');
            if (oldGutter) oldGutter.remove();
          }
          
          var container = document.getElementById('split');
          container.classList.toggle('vertical', mobile);
          
          splitInstance = Split(['#panel-left', '#panel-right'], {
            sizes: mobile ? [40, 60] : [60, 40],
            minSize: mobile ? [150, 150] : [250, 250],
            gutterSize: 8,
            direction: mode
          });
        }
      }, 150);
    });
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSplitMode);
  } else {
    initSplitMode();
  }
})();
