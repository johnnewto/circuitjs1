# CircuitJS1

## Introduction

CircuitJS1 is an electronic circuit simulator that runs in the browser. It was originally written by Paul Falstad as a Java Applet. It was adapted by Iain Sharp to run in the browser using GWT.
Here we have been extending the project with new components and features to support Economics modeling
For a hosted version of the application see:

* This Page: [https://johnnewto.github.io/circuitjs1/app.html/](https://johnnewto.github.io/circuitjs1/app.html)
* Paul's Page: [http://www.falstad.com/circuit/](http://www.falstad.com/circuit/)
* Iain's Page: [http://lushprojects.com/circuitjs/](http://lushprojects.com/circuitjs/)

Thanks to: Edward Calver for 15 new components and other improvements; Rodrigo Hausen for file import/export and many other UI improvements; J. Mike Rollins for the Zener diode code; Julius Schmidt for the spark gap code and some examples; Dustin Soodak for help with the user interface improvements; Jacob Calvert for the T Flip Flop; Ben Hayden for scope spectrum; Thomas Reitinger, Krystian Sławiński, Usevalad Khatkevich, Lucio Sciamanna, Mauro Hemerly Gazzani, J. Miguel Silva, and Franck Viard for translations; Andre Adrian for improved emitter coupled oscillator; Felthry for many examples; Colin Howell for code improvements. LZString (c) 2013 pieroxy.

## Building the web application

The web application is built using Google Web Toolkit (GWT), which compiles Java to JavaScript. Development is done locally using VS Code and the `dev.sh` helper script.

### Prerequisites

Install the required development tools:

```bash
sudo apt-get update
sudo apt-get install openjdk-8-jdk-headless
```

- **Java 8 JDK** - Required for GWT compilation

Optional dependencies:
```bash
sudo apt-get install ant php-cli php-curl
```

- **Ant** - Only needed if using `./dev.sh compile` (alternatively use Gradle)
- **PHP** - Optional, enables the URL shortener feature during local development (see [URL Shortener](#url-shortener-shortrelayphp) section)

### Initial Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/johnnewto/circuitjs1.git
   cd circuitjs1
   ```

2. Run the setup script to download GWT and configure the build:
   ```bash
   ./dev.sh setup
   ```
   This downloads GWT 2.10.0 and creates the necessary build configuration.

### Development Workflow

Start the development server:

```bash
./dev.sh start
```

This launches two services:
- **Web server** at http://localhost:8000 - Serves the application using PHP (if installed) or Python
- **GWT Code Server** at http://localhost:9876 - Provides live recompilation

> **Note:** The PHP server is used instead of Python when available because it can execute `shortrelay.php` for URL shortening. If PHP is not installed, the web server falls back to Python and the "Create short URL" button won't work locally.

Open http://localhost:8000/circuitjs.html in your browser.

**Live reload workflow:**
1. Edit Java source files in `src/com/lushprojects/circuitjs1/client/`
2. Save your changes
3. Reload the browser page - GWT automatically recompiles the modified code

### Building for Production

Compile the application for deployment:

```bash
./gradlew compileGwt
```

Or using Ant:

```bash
./dev.sh compile
```

The compiled output is placed in the `war/` directory.

### Available dev.sh Commands

| Command | Description |
|---------|-------------|
| `./dev.sh setup` | Install GWT and configure build environment |
| `./dev.sh start` | Start development server with live reload |
| `./dev.sh compile` | Compile for production deployment |

### Using VS Code

Open the project folder in VS Code for the best development experience:

```bash
code .
```

Recommended extensions:
- **Language Support for Java** - Syntax highlighting and IntelliSense
- **Debugger for Java** - Java debugging support

The project structure:
```
src/com/lushprojects/circuitjs1/client/   # Java source code
war/                                       # Web application output
  circuitjs.html                          # Main application HTML
  circuitjs1/                             # Compiled GWT output
```

## Deployment of the web application

After compiling with `./gradlew compileGwt` or `./dev.sh compile`, deploy the contents of the `war/` directory to your web server (excluding the `WEB-INF/` directory).

### Customization

- **circuitjs.html** - Customize the header to include your tracking, favicon, etc.
- **iframe.html** - Add branding content for the right-hand panel
- **shortrelay.php** - URL shortener relay (requires PHP, see below)
- **Dropbox integration** - Add your Dropbox API app-key to `circuitjs.html` if needed

### Directory structure

```
-+ Your web directory
  +- circuitjs.html      # Main application page
  +- iframe.html         # Branding panel content
  +- shortrelay.php      # URL shortener (optional, requires PHP)
  ++ circuitjs1/         # GWT compiled output
     +- circuits/        # Example circuits
     +- setuplist.txt    # Circuit directory index
```

## URL Shortener (shortrelay.php)

The "Export as URL" feature includes a "Create short URL" button that generates shortened URLs via tinyurl.com. This feature requires server-side PHP support because:

1. **CORS restrictions** - Browsers block direct JavaScript calls to third-party URL shortening APIs
2. **URL encoding** - The circuit data contains special characters that need server-side processing

### How it works

The `shortrelay.php` script acts as a relay between the browser and tinyurl.com:

```
Browser → shortrelay.php → tinyurl.com → shortrelay.php → Browser
```

The script receives the circuit URL, properly encodes it, calls tinyurl.com's API, and returns the shortened URL.

### Server requirements

- PHP with the `curl` extension enabled
- Outbound HTTP access to tinyurl.com

### Local development

For local development with PHP support, use:

```bash
./dev.sh start
```

This uses PHP's built-in server (if PHP is installed) which can execute `shortrelay.php`. Install PHP if needed:

```bash
sudo apt-get install php-cli php-curl
```

### Static hosting (GitHub Pages, etc.)

The URL shortener **does not work** on static hosting platforms like GitHub Pages, Netlify, or S3 because they cannot execute PHP.

To disable the "Create short URL" button, set `shortRelaySupported = false` in `src/com/lushprojects/circuitjs1/client/circuitjs1.java` before compiling.

### Custom URL shortener

You can modify `shortrelay.php` to use a different URL shortening service by changing the API endpoint:

```php
curl_setopt($ch, CURLOPT_URL, 'http://tinyurl.com/api-create.php?url='. $v);
```

Replace this with your preferred service's API.

## Embedding

You can link to the full page version of the application using the link shown above.

If you want to embed the application in another page then use an iframe with the src being the full-page version.

You can add query parameters to link to change the applications startup behaviour. The following are supported:
```
.../circuitjs.html?cct=<string> // Load the circuit from the URL (like the # in the Java version)
.../circuitjs.html?ctz=<string> // Load the circuit from compressed data in the URL
.../circuitjs.html?startCircuit=<filename> // Loads the circuit named "filename" from the "Circuits" directory
.../circuitjs.html?startCircuitLink=<URL> // Loads the circuit from the specified URL. CURRENTLY THE URL MUST BE A DROPBOX SHARED FILE OR ANOTHER URL THAT SUPPORTS CORS ACCESS FROM THE CLIENT
.../circuitjs.html?euroResistors=true // Set to true to force "Euro" style resistors. If not specified the resistor style will be based on the user's browser's language preferences
.../circuitjs.html?IECGates=true // Set to true to force IEC logic gates. If not specified the gate style will be based on the user's browser's language preferences
.../circuitjs.html?usResistors=true // Set to true to force "US" style resistors. If not specified the resistor style will be based on the user's browser's language preferences
.../circuitjs.html?whiteBackground=<true|false>
.../circuitjs.html?conventionalCurrent=<true|false>
.../circuitjs.html?running=<true|false> // Start the app without the simulation running, default true
.../circuitjs.html?hideSidebar=<true|false> // Hide the sidebar, default false
.../circuitjs.html?hideMenu=<true|false> // Hide the menu, default false
.../circuitjs.html?editable=<true|false> // Allow circuit editing, default true
.../circuitjs.html?positiveColor=%2300ff00 // change positive voltage color (rrggbb)
.../circuitjs.html?negativeColor=%23ff0000 // change negative voltage color
.../circuitjs.html?selectColor=%2300ffff // change selection color
.../circuitjs.html?currentColor=%23ffff00 // change current color
.../circuitjs.html?mouseWheelEdit=<true|false> // allow changing of values by mouse wheel
.../circuitjs.html?mouseMode=<item> // set the initial mouse mode.  can also initially perform other UI actions, such as opening the 'about' menu, running 'importfromlocalfile', etc.
.../circuitjs.html?hideInfoBox=<true|false>
```
The simulator can also interface with your javascript code.  See [war/jsinterface.html](http://www.falstad.com/circuit/jsinterface.html) for an example.

## Building an Electron application

The [Electron](https://electronjs.org/) project allows web applications to be distributed as local executables for a variety of platforms. This repository contains the additional files needed to build circuitJS1 as an Electron application.

The general approach to building an Electron application for a particular platform is documented [here](https://electronjs.org/docs/tutorial/application-distribution). The following instructions apply this approach to circuit JS.

To build the Electron application:
* Compile the application using GWT, as above.
* Download and unpack a [pre-built Electron binary directory](https://github.com/electron/electron/releases) version 9.3.2 for the target platform.
* Copy the "app" directory from this repository to the location specified [here](https://electronjs.org/docs/tutorial/application-distribution) in the Electron binary directory structure.
* Copy the "war" directory, containing the compiled CircuitJS1 application, in to the "app" directory the Electron binary directory structure.
* Run the "Electron" executable file. It should automatically load CircuitJS1.

Known limitations of the Electron application:
* "Create short URL" on "Export as URL" doesn't work as it relies on server support.

Thanks to @Immortalin for the initial work in applying Electron to CircuitJS1.

## License

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
