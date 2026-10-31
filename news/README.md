# Welcome to LunarTune News

![LunarTune News](images/banner.png)

This folder is the app's news feed. Anything dropped in here shows up under
**News** in the top-bar menu on the home screen — no app release needed.

## Adding a post

1. Create a file in this folder. It can be `.md` (markdown, recommended) or `.txt`
   (shown verbatim, no formatting).
2. Start the file name with the date if you want it sorted newest-first and shown
   with a date: `2026-11-01-whats-new.md`.
3. Put images in `images/` and reference them relatively:

```markdown
![Screenshot](images/banner.png)
```

4. Commit. Readers get it on their next refresh (the app refreshes at most once
   every 30 minutes, or when they tap the refresh button).

## How the app reads it

- The **list** is built from the folder listing; the **first `#` heading** becomes the
  title, the first paragraph becomes the summary, and the first image becomes the
  row thumbnail.
- Relative image and link paths are resolved against this folder, so
  `images/banner.png` works exactly as written.
- Articles are cached on the device, so news you have already opened stays readable
  offline — and a failed refresh never wipes what is already there.
- Files starting with `.` or `_` are skipped, so `_drafts/` and `_notes.md` stay out
  of the app. `README.md` is skipped too.

## What is supported

| Markdown | Notes |
|---|---|
| Headings, bold, italic, strikethrough | rendered |
| Lists, task lists, tables | rendered |
| Links | open in the browser |
| Images | `https://`, relative paths, and `<img src="...">` |
| Inline HTML | basic tags |

Nothing is executed and nothing is fetched at build time: the app reads the folder
over the network with the GitHub contents API for the listing and
raw.githubusercontent for the files. Ordinary text files are shown in a monospace
block so no character is interpreted.
