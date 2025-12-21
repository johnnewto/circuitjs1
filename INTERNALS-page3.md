# URL Shortener and Share Button Feature

This document describes the URL shortener integration and Share button feature in CircuitJS1.

## Overview

CircuitJS1 can create short URLs for sharing circuits. The feature uses a configurable relay service that calls the TinyURL API to create short links from the full circuit URLs (which can be quite long due to compressed circuit data).

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           User clicks Share                              │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  FloatingControlPanel.shareCircuit()                                     │
│  1. Gets circuit dump via sim.dumpCircuit()                              │
│  2. Compresses with LZString                                             │
│  3. Builds URL query: ?ctz=<compressed>&editable=false                   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Configuration Check (circuitjs.html)                                    │
│  - localhost → shortrelay.php (local PHP)                                │
│  - production → Cloudflare Worker URL                                    │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Relay Service                                                           │
│  - Receives encoded circuit URL                                          │
│  - Calls TinyURL API                                                     │
│  - Returns short URL                                                     │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Result                                                                  │
│  - Short URL copied to clipboard                                         │
│  - Toast notification displayed                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Configuration (circuitjs.html)

The relay URL is configured via JavaScript in `war/circuitjs.html`:

```javascript
<script>
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    window.shortRelayUrl = "shortrelay.php";
  } else {
    window.shortRelayUrl = "https://circuitjs-shortener.johnnewto.workers.dev";
  }
</script>
```

This auto-detects the environment:
- **Localhost**: Uses the local PHP relay (`shortrelay.php`)
- **Production**: Uses a Cloudflare Worker for serverless execution

### 2. ExportAsUrlDialog.java

Provides the core URL shortening functionality and configuration access:

```java
// Get the short relay URL from JavaScript (configured in circuitjs.html)
static public native String getShortRelayUrl() /*-{
    if ($wnd.shortRelayUrl !== undefined && $wnd.shortRelayUrl !== null && $wnd.shortRelayUrl !== '')
        return $wnd.shortRelayUrl;
    return null;
}-*/;
```

Key methods:
- `getShortRelayUrl()` - Native JSNI method to read `window.shortRelayUrl`
- `shortIsSupported()` - Returns true if relay URL is configured (and not in Electron)
- `createShort(String urlin)` - Makes HTTP request to create short URL

### 3. FloatingControlPanel.java

Implements the Share button in the floating control panel:

```java
// Share button - create short URL and copy to clipboard
if (isShortUrlSupported()) {
    createIconButton("cirjsicon-export", "Share Circuit (Copy Short URL)", e -> {
        shareCircuit((Button) e.getSource());
    });
}
```

Key methods:

#### `shareCircuit(Button shareButton)`
Main sharing logic:
1. Gets circuit dump via `sim.dumpCircuit()`
2. Compresses using LZString: `compress(dump)`
3. Builds URL with `?ctz=<compressed>&editable=false`
4. Shows loading spinner on button
5. Makes async HTTP request to relay
6. On success: copies to clipboard, shows toast notification
7. On error: shows alert dialog

#### `showShareNotification(String shortUrl)`
Displays a toast notification:
```java
private void showShareNotification(String shortUrl) {
    com.google.gwt.dom.client.DivElement toast = 
        com.google.gwt.dom.client.Document.get().createDivElement();
    toast.setInnerHTML("<i class=\"cirjsicon-export\"></i>&nbsp;" + 
        Locale.LS("Short URL copied!") + " <span class=\"toast-url\">" + shortUrl + "</span>");
    toast.setClassName("toast-notification");
    // ... append to body and auto-remove after 3.5 seconds
}
```

#### `copyToClipboard(String text)`
Native JSNI method using the reliable textarea approach:
```java
private native void copyToClipboard(String text) /*-{
    var textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    // ... styling to hide textarea
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    document.execCommand('copy');
    document.body.removeChild(textArea);
}-*/;
```

#### `compress(String dump)`
LZString compression (same as ExportAsUrlDialog):
```java
private native String compress(String dump) /*-{
    return $wnd.LZString.compressToEncodedURIComponent(dump);
}-*/;
```

### 4. Local PHP Relay (shortrelay.php)

For local development, `war/shortrelay.php` acts as a proxy to TinyURL:

```php
<?php
$serveraddr='http' . (isset($_SERVER['HTTPS']) ? 's' : '') . '://' . "{$_SERVER['HTTP_HOST']}";
$s=parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$path= str_replace('shortrelay.php','circuitjs.html',$s);
$ch = curl_init();
$v=urlencode($serveraddr . $path . $_GET["v"]);
curl_setopt($ch, CURLOPT_URL, 'http://tinyurl.com/api-create.php?url='. $v);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
echo curl_exec($ch);
exit;
?>
```

**Requirements**: PHP with curl extension enabled.

**Note**: The `dev.sh` script automatically uses PHP's built-in server when PHP is available:
```bash
php -S 0.0.0.0:8000 -t war/
```

### 5. Toast Notification Styling (style.css)

The toast notification uses a yellow theme and slides in from the right:

```css
.toast-notification {
    position: fixed;
    top: 60px;
    right: 20px;
    transform: translateX(100%);
    background: linear-gradient(to bottom, #fff9e6 0%, #fff3cc 100%);
    color: #5a4a00;
    padding: 12px 20px;
    border-radius: 6px;
    font-size: 14px;
    z-index: 10002;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
    border: 1px solid #e6d280;
    display: flex;
    align-items: center;
    gap: 8px;
    animation: toastSlideIn 3.5s ease-in-out forwards;
}

@keyframes toastSlideIn {
    0%   { transform: translateX(100%); opacity: 0; }
    10%  { transform: translateX(0); opacity: 1; }
    85%  { transform: translateX(0); opacity: 1; }
    100% { transform: translateX(100%); opacity: 0; }
}

.share-spinner {
    animation: spin 1s linear infinite;
    display: inline-block;
}
```

## Cloudflare Worker (Production)

For GitHub Pages or other static hosting, a Cloudflare Worker provides serverless URL shortening:

```javascript
export default {
  async fetch(request) {
    const url = new URL(request.url);
    const longUrl = url.searchParams.get('v');
    
    if (!longUrl) {
      return new Response('Missing v parameter', { status: 400 });
    }

    const decodedUrl = decodeURIComponent(longUrl);
    const fullUrl = 'https://johnnewto.github.io/circuitjs1/circuitjs.html' + decodedUrl;
    const tinyUrlApi = 'https://tinyurl.com/api-create.php?url=' + encodeURIComponent(fullUrl);

    const response = await fetch(tinyUrlApi);
    const shortUrl = await response.text();

    return new Response(shortUrl, {
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Content-Type': 'text/plain',
      },
    });
  },
};
```

**Cloudflare Free Tier**: 100,000 requests/day

## Data Flow Summary

1. **User Action**: Click Share button in FloatingControlPanel
2. **Circuit Serialization**: `sim.dumpCircuit()` returns circuit text format
3. **Compression**: LZString compresses data (~70-80% size reduction)
4. **URL Building**: Creates `?ctz=<compressed>&editable=false` query
5. **Relay Request**: Async HTTP GET to configured relay URL
6. **TinyURL API**: Relay calls TinyURL to create short link
7. **Clipboard Copy**: Short URL copied using textarea/execCommand
8. **User Feedback**: Toast notification slides in from top-right

## Configuration Options

| Environment | Relay URL | Notes |
|-------------|-----------|-------|
| localhost | `shortrelay.php` | Requires PHP with curl |
| Production | Cloudflare Worker URL | Free tier: 100K/day |
| Custom | Any compatible relay | Must accept `?v=<encoded_url>` |

## Files Modified/Created

| File | Purpose |
|------|---------|
| `war/circuitjs.html` | Configuration script for `window.shortRelayUrl` |
| `ExportAsUrlDialog.java` | `getShortRelayUrl()` native method |
| `FloatingControlPanel.java` | Share button, clipboard, toast notification |
| `style.css` | Toast notification styles and animations |
| `shortrelay.php` | Local PHP relay to TinyURL API |
| `dev.sh` | Modified to use PHP server when available |

## Security Considerations

- **TinyURL API**: Public, no authentication required
- **CORS**: Cloudflare Worker must return `Access-Control-Allow-Origin: *`
- **Rate Limiting**: TinyURL may rate-limit excessive requests
- **URL Length**: Very complex circuits may exceed URL limits (~2000 chars recommended)
