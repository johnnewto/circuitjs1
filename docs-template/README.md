# CircuitJS1 Documentation Site

This repository contains the Quarto-based documentation website for [CircuitJS1](https://github.com/johnnewto/circuitjs1), an electronic circuit simulator.

## Overview

The site includes:
- **Interactive Application**: Embedded CircuitJS1 simulator
- **Documentation**: User guides, tutorials, and examples
- **Development Resources**: Building instructions and contribution guidelines

## Local Development

### Prerequisites
- [Quarto](https://quarto.org/docs/get-started/) installed
- Node.js (for any additional dependencies)

### Setup

```bash
# Clone this documentation repository
git clone https://github.com/johnnewto/circuitjs1-docs.git
cd circuitjs1-docs

# Preview the site locally
quarto preview
```

The site will be available at http://localhost:4200

### Building

**Important**: The CircuitJS application must be compiled for production first.

```bash
# Complete build (compiles CircuitJS + docs)
./build.sh

# Quick docs-only build (when only changing documentation)
./build-docs-only.sh

# Or build step by step:
cd ../                    # Go to main CircuitJS repo
./dev.sh compile         # Compile CircuitJS for production
cd docs-template         # Return to docs
quarto render           # Render documentation
./copy-circuit-files.sh # Copy compiled CircuitJS files

# Output will be in _site/ directory
```

### Development Setup

The documentation site needs access to the CircuitJS1 application files from the main repository's `war` directory. This is handled automatically by the build scripts:

- `copy-circuit-files.sh` - Copies CircuitJS files to the `_site` directory
- `build.sh` - Complete build script (render + copy files)

### Local Testing

```bash
# After building, serve the site locally
cd _site
python3 -m http.server 8080
# Then open http://localhost:8080
```

**Note**: The CircuitJS application will only work properly when compiled for production. If you see errors about "Super Dev Mode server at port 9876", run `./build.sh` to ensure the production build is used.

## Deployment

### GitHub Pages Setup

To deploy this documentation site to GitHub Pages:

#### 1. Quick Setup (Recommended)
```bash
# Run the automated setup script from the main repo root
cd /path/to/circuitjs1  # Main repository root
docs-template/setup-deployment.sh

# Follow the instructions to enable GitHub Pages
```

#### 2. Manual Repository Setup
```bash
# Option A: Separate docs repository
git clone https://github.com/yourusername/circuitjs1-docs.git

# Option B: Use docs-template folder in main CircuitJS1 repo
# (This is the current setup)
```

#### 3. GitHub Pages Configuration
1. Go to your repository **Settings** → **Pages**
2. Under **Source**, select **"GitHub Actions"**
3. This enables custom GitHub Actions workflows for deployment

#### 4. GitHub Actions Workflow
The workflow file is created at `.github/workflows/deploy-docs.yml` in the main repository root.

**Key features:**
- **Automatic builds**: Triggers on pushes to `main`/`master` branch
- **PR validation**: Tests builds on pull requests without deploying
- **CircuitJS compilation**: Automatically compiles CircuitJS1 for production
- **Site validation**: Checks that all required files are generated

**Workflow steps:**
1. Checkout repository with full history
2. Setup Java 11 and Quarto
3. Compile CircuitJS1 using `./dev.sh compile`
4. Build documentation using `./build-docs-only.sh`
5. Deploy to GitHub Pages

**Triggers:**
- Push to main/master branch with changes in:
  - `docs-template/**` (documentation changes)
  - `src/**` (CircuitJS source changes)
  - `war/**` (CircuitJS build output changes)
- Pull requests for validation (doesn't deploy)

#### 5. Repository Structure for DeploymentFor **main CircuitJS1 repo** (current setup):
```
circuitjs1/
├── docs-template/           # Documentation source
│   ├── .github/workflows/  # Deployment workflows  
│   ├── _quarto.yml
│   ├── index.qmd
│   └── app.qmd
├── war/                    # CircuitJS compiled files
└── dev.sh                  # Build script
```

For **separate docs repo**:
```
circuitjs1-docs/
├── .github/workflows/      # Deployment workflows
├── circuitjs1/            # Git submodule to main repo
├── _quarto.yml
├── index.qmd
└── app.qmd
```

#### 6. Environment Variables (if needed)
If using a separate repository, you may need to configure:
- Repository secrets for accessing the main CircuitJS1 repo
- Submodule configuration for automated builds

#### 7. Troubleshooting Deployment

**Common Issues:**

- **Deprecated action versions**: If you see errors about deprecated actions (v3), run:
  ```bash
  docs-template/validate-actions.sh  # Check for deprecated versions
  ```
- **Build fails**: Check Java version (needs Java 11+) and GWT compilation
- **CircuitJS not loading**: Ensure production compilation runs before doc build
- **Pages not updating**: Check GitHub Pages settings and workflow permissions
- **404 on deployment**: Verify the `_site` folder contains all necessary files

**Debugging steps:**
1. Check GitHub Actions logs in the "Actions" tab
2. Verify file structure in the deployment artifact
3. Test local build with `./build.sh` first
4. Run `validate-actions.sh` to check for deprecated action versions

#### 8. Custom Domain (Optional)
To use a custom domain:
1. Add a `CNAME` file to `docs-template/` with your domain
2. Configure DNS A records to point to GitHub Pages IPs
3. Enable "Enforce HTTPS" in repository settings

**Final Result:**
- Documentation site: `https://username.github.io/repository-name`
- Interactive CircuitJS1 app at: `https://username.github.io/repository-name/app.html`

The complete deployment workflow:
1. Compiles CircuitJS1 application from source (`./dev.sh compile`)
2. Renders the Quarto documentation site
3. Copies CircuitJS files to the documentation site
4. Deploys everything to GitHub Pages

## Project Structure

```
├── _quarto.yml          # Quarto configuration
├── index.qmd            # Homepage
├── app.qmd              # Application page (embeds CircuitJS1)
├── docs/                # Documentation pages
│   ├── user-guide.qmd   # User documentation
│   ├── tutorials.qmd    # Learning materials
│   ├── examples.qmd     # Circuit examples
│   ├── building.qmd     # Development setup
│   └── contributing.qmd # Contribution guidelines
├── assets/              # Static assets (images, etc.)
├── styles.css           # Custom CSS
└── .github/workflows/   # GitHub Actions workflows
```

## Content Updates

### Adding Documentation
1. Create new `.qmd` files in the `docs/` directory
2. Update `_quarto.yml` to include new pages in navigation
3. Use standard Quarto markdown syntax

### Adding Examples
Circuit examples can be added by:
1. Creating circuit files in CircuitJS1
2. Exporting the circuit definition
3. Adding to the examples documentation with embedded previews

### Updating the Application
The CircuitJS1 application is automatically updated from the main repository during deployment. No manual intervention needed.

## Contributing

1. Fork this repository
2. Create a feature branch
3. Make your changes
4. Test locally with `quarto preview`
5. Submit a pull request

## License

This documentation site is released under the same license as CircuitJS1. The CircuitJS1 application is GPL licensed.

## Links

- [CircuitJS1 Source Code](https://github.com/johnnewto/circuitjs1)
- [Live Documentation Site](https://johnnewto.github.io/circuitjs1-docs)
- [Quarto Documentation](https://quarto.org/docs/)