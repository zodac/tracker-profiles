# Code Quality Config Overrides

## Overview

This project's own linting configuration: the files that override or extend the shared rules in the
[code-quality-config](../code-quality-config) submodule, rather than replacing them. They live here, in one tracked
directory named for the submodule they layer over, instead of as dotfiles at the repo root.

Nothing here is found by convention. Each tool is pointed at its file explicitly, so a scan run by hand must do the
same.

## Tools

### Java

#### [Checkstyle](https://checkstyle.org/)

- [checkstyle-suppression.xml](./checkstyle-suppression.xml) — the files/rules this project exempts, **on top of** the
  submodule's shared suppressions rather than instead of them: the shared `checkstyle.xml` declares a second, optional
  `SuppressionFilter` for a project-supplied file, and both filters apply. The file is named by the
  `<checkstyle-project-suppression-file>` property in the root `pom.xml` (and re-pointed at the project root by
  `tracker-profiles-screenshots/pom.xml`). The filter is `optional="true"`, so a property naming a file that does not
  exist gives **silently zero** project suppressions rather than an error.
