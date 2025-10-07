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

```bash
# Render the complete site
quarto render

# Output will be in _site/ directory
```

## Deployment

The site is automatically deployed to GitHub Pages via GitHub Actions when changes are pushed to the `main` branch.

The deployment workflow:
1. Builds the latest CircuitJS1 application from source
2. Copies the application files to the documentation site
3. Renders the Quarto site
4. Deploys to GitHub Pages

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