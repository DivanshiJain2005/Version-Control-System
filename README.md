# myvcs (minimal VCS in Java)

This is a tiny, local-only version control system inspired by Git. It supports:

- `init`
- `add <path>`
- `commit -m "message"`
- `status`
- `log`
- `checkout <branch|commit>`
- `checkout -b <branch>`
- `branch [name]`
- `diff [--staged]`

## Quick Start

Compile and run:

```bash
.\myvcs.bat init
.\myvcs.bat add .
.\myvcs.bat commit -m "first commit"
.\myvcs.bat status
.\myvcs.bat log
.\myvcs.bat branch dev
.\myvcs.bat checkout dev
.\myvcs.bat diff
```

Or with PowerShell:

```powershell
.\myvcs.ps1 init
.\myvcs.ps1 add .
.\myvcs.ps1 commit -m "first commit"
```

## Repo Layout

```
.myvcs/
  objects/
  refs/heads/
  HEAD
  index
```

## Object Format

Each object is stored as:

```
<type> <size>\n<body>
```

The SHA-256 hash of the header + body is the object id.
