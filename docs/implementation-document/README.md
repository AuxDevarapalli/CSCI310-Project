# Project 2.3 implementation document

[`Team04_Project2.3_Implementation_Document.pdf`](Team04_Project2.3_Implementation_Document.pdf) is the document we submit on Brightspace. It is built from the files in this folder:

| File | What it is |
|---|---|
| `index.html` | The document text. Edit this. |
| `diagrams/*.puml` | PlantUML sources of Figures 2a to 10 (shared look in `diagrams/style.iuml`) |
| `diagrams/*.svg` | The rendered diagrams that `index.html` shows |
| `tools/fig1.py` | Draws Figure 1, the block diagram, as `diagrams/fig1-architecture.svg` |
| `build.sh` | Prints the PDF with Google Chrome (macOS) |

After changing the text, run `./build.sh`.

After changing a diagram, run `PLANTUML_JAR=/path/to/plantuml-1.2026.9.jar ./build.sh`. The jar comes from the [PlantUML releases](https://github.com/plantuml/plantuml/releases); re-rendering also needs Java, and Python 3 with PyMuPDF for Figure 1.
