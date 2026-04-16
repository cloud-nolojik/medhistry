# Install the `sdd-new` script

This folder contains one file: `bin/sdd-new` — a bash script that bootstraps a new Spec-Driven Development project from your `cloud-nolojik/sdd-starter` template, seeds the one-line pitch, opens your editor, and launches Claude Code with a kickoff prompt.

## Install (one-time)

1. Move the script into your sdd-starter repo:
   ```bash
   mkdir -p ~/path/to/sdd-starter/bin
   cp bin/sdd-new ~/path/to/sdd-starter/bin/sdd-new
   chmod +x ~/path/to/sdd-starter/bin/sdd-new
   ```

2. Commit it to the template so future "Use this template" clones include it:
   ```bash
   cd ~/path/to/sdd-starter
   git add bin/sdd-new
   git commit -m "chore: add sdd-new bootstrap script"
   git push
   ```

3. Symlink it into your PATH so you can run `sdd-new` from anywhere:
   ```bash
   ln -s ~/path/to/sdd-starter/bin/sdd-new /usr/local/bin/sdd-new
   # or, if /usr/local/bin isn't writable:
   mkdir -p ~/bin && ln -s ~/path/to/sdd-starter/bin/sdd-new ~/bin/sdd-new
   # and make sure ~/bin is in your PATH (add to ~/.zshrc):
   # export PATH="$HOME/bin:$PATH"
   ```

4. Verify:
   ```bash
   which sdd-new
   sdd-new --help 2>/dev/null || sdd-new < /dev/null  # should print the check
   ```

## Prerequisites

- `git` (already on macOS)
- `gh` (GitHub CLI) — install with `brew install gh`, then `gh auth login`
- `claude` CLI — optional but recommended (the script will launch it for you)
- An editor command on PATH (`code` for VS Code, `cursor`, `webstorm`, or `open`)

## Usage

```bash
sdd-new
```

It will ask for:

- **Project name** — human-friendly (e.g. `Agent Clinic`)
- **Repo slug** — auto-generated from the name; you can override
- **One-line pitch** — one sentence, what + for whom
- **GitHub org/user** — defaults to `cloud-nolojik`
- **Template repo** — defaults to `cloud-nolojik/sdd-starter`
- **Visibility** — `public` or `private`
- **Local clone parent dir** — defaults to `~/Projects`
- **Editor command** — defaults to `$EDITOR` or `code`
- **Launch Claude?** — defaults to yes

Then it:

1. Creates the GitHub repo from your template (`gh repo create --template …`)
2. Clones it into `~/Projects/<slug>`
3. Injects your one-line pitch into `specs/mission.md`
4. Writes `.sdd-kickoff.md` with instructions for Claude
5. Commits and pushes the seed
6. Opens your editor
7. Launches `claude` with a prompt that says "read `.sdd-kickoff.md` and follow it"

At that point Claude runs the `constitution-init` skill interview, drafts the three Constitution files, commits them on a branch. You review, merge, then loop to the feature-spec step.

## Tweaking defaults

Open the script and edit the `DEFAULT_*` constants at the top:

```bash
DEFAULT_ORG="cloud-nolojik"
DEFAULT_TEMPLATE="cloud-nolojik/sdd-starter"
DEFAULT_VISIBILITY="private"
DEFAULT_CLONE_DIR="${HOME}/Projects"
DEFAULT_EDITOR="${EDITOR:-code}"
```

Change these once, never type them again.

## Troubleshooting

- **"Not logged in to gh"** → run `gh auth login`, pick GitHub.com, HTTPS, authenticate via browser.
- **"Repo already exists"** → pick a different slug, or delete the existing repo first.
- **"claude CLI not found"** → the script prints the kickoff prompt for you to paste into Claude Code manually.
- **Editor doesn't open** → set `EDITOR_CMD=none` when prompted; open the folder yourself.
