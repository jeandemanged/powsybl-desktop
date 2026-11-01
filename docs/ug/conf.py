# Configuration file for the Sphinx documentation builder.
#
# This file only contains a selection of the most common options. For a full
# list see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import datetime
import os
import re

source_path = os.path.abspath('../..')


# -- Project information -----------------------------------------------------

project = 'powsybl-desktop'
module_name = "powsybl-desktop"
github_repository = "https://github.com/jeandemanged/powsybl-desktop/"

# Build year for the copyright
copyright_year = f'2026-{ datetime.datetime.now().year }'

# Find the version and release information.
# We have a single source of truth for our version number: the project's pom.xml file.
# This next bit of code reads from it.
file_with_version = os.path.join(source_path, "pom.xml")
with open(file_with_version) as f:
    next_line_contains_version = False
    for line in f:
        if next_line_contains_version == False:
            m = re.match(r'^ {4}\<artifactId\>' + module_name + r'\<\/artifactId\>', line)
            if m:
                next_line_contains_version = True
        else:
            m = re.match(r'^ {4}\<version\>(.*)\<\/version\>', line)
            if m:
                __version__ = m.group(1)
                # The short X.Y version.
                version = ".".join(__version__.split(".")[:2])
                # The full version, including alpha/beta/rc tags.
                release = __version__
                break
    else:  # AKA no-break
        version = release = "dev"


# -- General configuration ---------------------------------------------------

extensions = ['sphinx.ext.autosectionlabel',
              'sphinx.ext.todo',
              'sphinx.ext.intersphinx',
              'sphinx_tabs.tabs',
              'myst_parser',
              # Extension used to add a "copy" button on code blocks
              'sphinx_copybutton']
myst_enable_extensions = [
    "amsmath",
    "colon_fence",
    "dollarmath",
    "attrs_inline"
]
myst_heading_anchors = 6

# Add any paths that contain templates here, relative to this directory.
templates_path = ['_templates']

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
# This pattern also affects html_static_path and html_extra_path.
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store', 'README.md']

# Reference sections generation
autosectionlabel_prefix_document = True
autosectionlabel_maxdepth = 2


# -- Options for HTML output -------------------------------------------------

html_theme = "furo"

html_title = f"{project} v{release}"

html_logo = '_static/logos/logo_lfe_powsybl.svg'
html_favicon = "_static/favicon.ico"

html_context = {
    "copyright_year": copyright_year,
    "sidebar_logo_href": "https://powsybl.readthedocs.io/",
    "github_repository": github_repository
}

html_theme_options = {
    # the following 3 lines enable edit button
    "source_repository": github_repository,
    "source_branch": "main",
    "source_directory": "docs/ug/",
}

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
html_static_path = ['_static']
html_css_files = ['styles/styles.css']

todo_include_todos = True


# -- Options for LaTeX (PDF) output ------------------------------------------

# The PDF file name is what the application looks for in its help menu.
latex_documents = [
    ('index', 'powsybl-desktop-user-guide.tex', "PowSyBl Desktop User's Guide", 'PowSyBl Desktop authors', 'manual'),
]

# Links to external documentations.
# PowSyBl versions come from the powsybl-dependencies BOM, not from pom.xml properties: link to latest.
intersphinx_mapping = {
    "powsyblcore": ("https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/", None),
    "powsyblopenloadflow": ("https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/", None)
}
intersphinx_disabled_reftypes = ["*"]
