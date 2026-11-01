# PowSyBl Desktop user's guide

These are the documentation sources for the PowSyBl Desktop user's guide, built as HTML and PDF.

Please keep them up to date with your developments.

## Build the documentation

Navigate to the "docs/ug" directory of the project:
~~~bash
cd docs/ug
~~~
Generate the screenshots, from the repository root (they are not committed and differ between operating systems):
~~~bash
mvn test -Pug-screenshots
~~~
Install the requirements the first time (Sphinx 9 requires Python 3.12 or later):
~~~bash
pip install -r requirements.txt
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
