# URL Shortener Setup Guide

The "Export as URL" feature includes a "Create short URL" button that generates shortened URLs via tinyurl.com. This document explains how to configure the URL shortener for different environments.

## How It Works

The URL shortener requires a server-side relay because browsers block direct JavaScript calls to third-party URL shortening APIs (CORS restrictions).

```
Browser → Relay (PHP or Cloudflare Worker) → tinyurl.com → Relay → Browser
```

## Configuration

The relay URL is configured in `war/circuitjs.html` via JavaScript:

```javascript
window.shortRelayUrl = "https://your-relay-url";
```

The current configuration auto-detects the environment:

| Environment | Relay Used |
|-------------|------------|
| `localhost` / `127.0.0.1` | Local PHP (`shortrelay.php`) |
| Production (GitHub Pages, etc.) | Cloudflare Worker |

## Local Development Setup

### Prerequisites

Install PHP with curl extension:

```bash
sudo apt-get install php-cli php-curl
```

### Running

Start the development server:

```bash
./dev.sh start
```

This uses PHP's built-in server which can execute `shortrelay.php`.

The "Create short URL" button will automatically use the local PHP relay.

## Production Setup (GitHub Pages)

GitHub Pages only serves static files and cannot execute PHP. Use a Cloudflare Worker instead.

### Step 1: Create Cloudflare Account

1. Go to [cloudflare.com](https://www.cloudflare.com/) and sign up (free)
2. No domain required - Workers get a free `*.workers.dev` subdomain

### Step 2: Create the Worker

1. Go to **Workers & Pages** in the Cloudflare dashboard
2. Click **Create Application** → **Create Worker**
3. Name it (e.g., `circuitjs-shortener`)
4. Click **Deploy**

### Step 3: Add Worker Code

Click **Edit Code** and replace with:

```javascript
export default {
  async fetch(request) {
    const url = new URL(request.url);
    const circuitUrl = url.searchParams.get('v');
    
    if (!circuitUrl) {
      return new Response('Missing URL parameter', { status: 400 });
    }
    
    // Build the full circuit URL - update this to your GitHub Pages URL
    const baseUrl = 'https://johnnewto.github.io/circuitjs1/circuitjs.html';
    const fullUrl = baseUrl + circuitUrl;
    
    // Call tinyurl API
    const tinyResponse = await fetch(
      'http://tinyurl.com/api-create.php?url=' + encodeURIComponent(fullUrl)
    );
    
    // Return with CORS headers
    return new Response(await tinyResponse.text(), {
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Content-Type': 'text/plain'
      }
    });
  }
};
```

Click **Save and Deploy**.

### Step 4: Configure circuitjs.html

Edit `war/circuitjs.html` and update the Cloudflare Worker URL:

```html
<script>
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    window.shortRelayUrl = "shortrelay.php";
  } else {
    window.shortRelayUrl = "https://your-worker.your-subdomain.workers.dev";
  }
</script>
```

### Step 5: Deploy

Recompile and deploy to GitHub Pages:

```bash
./gradlew compileGwt
# Deploy war/ directory to GitHub Pages
```

## Optional: Restrict Worker Access

To prevent others from using your Cloudflare Worker, add origin checking:

```javascript
export default {
  async fetch(request) {
    // Only allow requests from your domains
    const origin = request.headers.get('Origin');
    const allowedOrigins = [
      'https://johnnewto.github.io',
      'http://localhost:8000',
      'http://127.0.0.1:8000'
    ];
    
    if (origin && !allowedOrigins.includes(origin)) {
      return new Response('Forbidden', { status: 403 });
    }
    
    // ... rest of the code
  }
};
```

## Disabling URL Shortener

To disable the "Create short URL" button entirely, set the relay URL to null in `circuitjs.html`:

```html
<script>window.shortRelayUrl = null;</script>
```

Or simply remove/comment out the script block.

## Privacy & Security

### What's Publicly Visible

When deploying to GitHub Pages, the Cloudflare Worker URL is visible in the HTML source:

| Item | Visible in Source? | Security Risk |
|------|-------------------|---------------|
| Cloudflare Worker URL | ✅ Yes | **Low** - It's a public endpoint |
| Cloudflare account credentials | ❌ No | None |
| Cloudflare API keys | ❌ No | None |
| Worker source code | ❌ No | None |

The worker can only shorten URLs - it has no access to your Cloudflare account.

### Potential Concerns

#### 1. Quota Abuse (Low Risk)

Anyone who views your `circuitjs.html` source can call your Cloudflare Worker, potentially using your 100K free requests/day quota.

**Mitigation:** Add origin checking to your worker:

```javascript
export default {
  async fetch(request) {
    // Only allow requests from your domains
    const origin = request.headers.get('Origin');
    const allowedOrigins = [
      'https://johnnewto.github.io',
      'http://localhost:8000',
      'http://127.0.0.1:8000'
    ];
    
    if (origin && !allowedOrigins.includes(origin)) {
      return new Response('Forbidden', { status: 403 });
    }
    
    // ... rest of the shortener code
  }
};
```

#### 2. Shortened URLs Always Point to Your Domain (No Risk)

The worker hardcodes your GitHub Pages URL as the base, so shortened URLs always point to your site. An attacker cannot use your worker to shorten arbitrary URLs.

#### 3. No Sensitive Data Transmitted (No Risk)

The worker only sends circuit data (already public in the URL) to tinyurl.com. No user credentials or private data are involved.

### Security Verdict: ✅ Reasonably Secure

The architecture is safe. The only consideration is potential quota abuse, which can be mitigated with origin checking if needed.

## Costs

- **Cloudflare Workers**: Free tier includes 100,000 requests/day
- **Local PHP**: No cost

## Troubleshooting

### Button not appearing

- Check browser console for JavaScript errors
- Verify `window.shortRelayUrl` is set correctly
- In Electron app, the button is always hidden

### "Shortener error" message

- Check if the relay URL is accessible
- Verify tinyurl.com is not blocked
- Check browser network tab for CORS errors

### PHP not working locally

- Ensure PHP is installed: `php -v`
- Ensure curl extension is enabled: `php -m | grep curl`
- Check that `./dev.sh start` is using PHP (not Python)
