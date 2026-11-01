# PowSyBl Desktop user's guide

These are the documentation sources for the PowSyBl Desktop user's guide, built as HTML and PDF.

Please keep them up to date with your developments.

## Requirements

- **JDK 25 and Maven**, as for building the application: the screenshots are taken by a test of the application.
- **Python 3.12 or later** (required by Sphinx 9), with the packages of `requirements.txt`:
  ~~~bash
  pip install -r docs/ug/requirements.txt
  ~~~
- **For the PDF only**, a LaTeX distribution providing `pdflatex` and `latexmk`, which `make latexpdf` runs:
  - Linux (Debian/Ubuntu): `sudo apt install latexmk texlive-latex-recommended texlive-latex-extra
    texlive-fonts-recommended tex-gyre`;
  - macOS: [MacTeX](https://www.tug.org/mactex/);
  - Windows: [MiKTeX](https://miktex.org/), set to install missing packages on the fly, and a Perl distribution
    such as [Strawberry Perl](https://strawberryperl.com/), which `latexmk` needs.
- **`make`** on Linux and macOS; on Windows, `make.bat` takes its place (`make html` runs it from `cmd` or
  PowerShell).

## Build the documentation

Generate the screenshots, from the repository root (they are not committed and differ between operating systems):
~~~bash
mvn test -Pug-screenshots
~~~
Navigate to the "docs/ug" directory of the project:
~~~bash
cd docs/ug
~~~
Build the documentation:
~~~bash
sphinx-build -a . _build/html
~~~
Or
~~~bash
make html
~~~
Or to build the PDF (requires a LaTeX distribution):
~~~bash
make latexpdf
~~~

### Preview the result

Then open `_build/html/index.html` in your browser, or `_build/latex/powsybl-desktop-user-guide.pdf`.

To link to another PowSyBl documentation, add it to `intersphinx_mapping` in `conf.py`, then use for example:
~~~Markdown
- [load flow configuration](inv:powsyblcore:*:*#simulation/loadflow/configuration).
~~~

*Note: if the build fails, try with the `-E` option to clear the cache:*
~~~bash
sphinx-build -a -E . _build/html
~~~
