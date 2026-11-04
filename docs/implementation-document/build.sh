#!/bin/zsh
# Rebuilds Team04_Project2.3_Implementation_Document.pdf from index.html and the diagrams.
#
#   ./build.sh                 print the PDF from index.html and the existing diagrams/*.svg
#   PLANTUML_JAR=~/plantuml.jar ./build.sh
#                              also re-render diagrams/*.puml first (after editing a diagram)
#
# Needs Google Chrome. Re-rendering needs Java and the PlantUML jar
# (github.com/plantuml/plantuml/releases); Figure 1 is drawn by tools/fig1.py (Python 3 + PyMuPDF).
set -e
DIR=${0:A:h}
OUT=${1:-$DIR/Team04_Project2.3_Implementation_Document.pdf}
if [[ -n "$PLANTUML_JAR" ]]; then
  (cd $DIR/diagrams && java -Djava.awt.headless=true -jar "$PLANTUML_JAR" -tsvg *.puml)
  python3 -E $DIR/tools/fig1.py $DIR/diagrams/fig1-architecture.svg > /dev/null
fi
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
  --no-pdf-header-footer --print-to-pdf="$OUT" "file://$DIR/index.html" 2>/dev/null
echo "Wrote $OUT"
