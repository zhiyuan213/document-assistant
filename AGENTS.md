# Repository checks

Before every commit, run the checks in `scripts/pre-commit-check.ps1` or let the configured pre-commit hook run them. Never bypass failed checks. This includes backend tests, frontend polling tests, mini-program and H5 builds, compiled page/module/icon checks, and Git diff validation.

Configure a fresh checkout with `git config core.hooksPath .githooks`. Preserve mini-program compatibility module paths when replacing existing modules because WeChat Devtools can retain incremental compilation references.

Do not describe real-device upload, module loading, document opening, or file sharing as verified based only on a successful build. Report those as pending until actually tested on WeChat. Do not upload user private documents as test fixtures; use synthetic samples.
