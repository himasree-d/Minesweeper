#  Minesweeper — Enhanced Edition

A fully-featured Minesweeper game built in Java Swing with difficulty levels, themes, a timer, sound effects, a hint system, and save/load support.

![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk) ![Swing](https://img.shields.io/badge/UI-Java%20Swing-blue) ![License](https://img.shields.io/badge/license-MIT-green)

---

##  How to Play

| Action | Control |
|---|---|
| Reveal a cell | Left-click |
| Flag / unflag a mine | Right-click (cycles: 🚩 → ? → blank) |
| Chord reveal (auto-reveal safe neighbours) | Left-click or middle-click on a revealed number |
| New game | `F2` or click the emoji button |
| Hint | `H` key or **Options → Hint** |

> **Mac users:** Two-finger tap = right-click

---

## Features

- **3 Difficulty Levels**
  - Beginner — 8×8 grid, 10 mines
  - Intermediate — 16×16 grid, 40 mines
  - Expert — 30×16 grid, 99 mines

- **Safe First Click** — mines are placed after your first click, so you can never die immediately

- **Timer & Best Times** — tracks your fastest time per difficulty, saved permanently between sessions

- **3 Themes** — Classic, Dark, Pastel — switchable live from the Theme menu

- **Sound Effects** — synthesised click, flag, explosion, and win fanfare (no audio files needed)

- **Hint System** — highlights a cell that is logically deducible as safe

- **Save / Load** — save your game mid-session and resume later

- **Chord Reveal** — click a revealed number whose flag count matches it to auto-reveal all safe neighbours

---

## Getting Started

### Requirements
- Java 8 or higher ([Download JDK](https://adoptium.net))

### Run from source

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/minesweeper.git
cd minesweeper

# 2. Compile
javac Minesweeper.java

# 3. Run
java Minesweeper
```

### Run the JAR (if provided)

```bash
java -jar minesweeper.jar
```

---

## Build a JAR (optional)

```bash
javac Minesweeper.java
jar cfe minesweeper.jar Minesweeper *.class
java -jar minesweeper.jar
```

---

## Project Structure

```
minesweeper/
├── Minesweeper.java   # Full source — single file, no dependencies
├── README.md
└── minesweeper_save.dat  # Auto-created when you save a game
```

---

## Screenshots

> *(Add your own screenshots here after running the game)*  
> Tip: On Mac use `Cmd + Shift + 4` to capture a window.
> <img width="556" height="659" alt="image" src="https://github.com/user-attachments/assets/341cf9b6-a77f-499a-bd22-d036f0f98888" />


---

## FAQ

**Can I play this in a browser?**  
No — Java Swing runs as a desktop app only. See [Deployment](#-deployment) below.

**Where are my best times stored?**  
In your system's Java Preferences (registry on Windows, plist on Mac). They persist across runs automatically.

**The sound doesn't work — why?**  
Some systems restrict audio. You can toggle sound off via **Options → Sound Effects**.

---

## Deployment

| Platform | Support |
|---|---|
| **GitHub** (source hosting) | ✅ Yes — push source and others can compile & run |
| **GitHub Releases** | ✅ Yes — attach a `.jar` so people can download and run directly |
| **GitHub Pages** | ❌ No — Pages only hosts static websites, not Java desktop apps |
| **Browser / Web** | ❌ Not without rewriting in JavaScript or using Java WebStart (deprecated) |

### How to add a Release with a JAR

1. Build the JAR (see above)
2. Go to your GitHub repo → **Releases → Draft a new release**
3. Attach `minesweeper.jar`
4. Users download it and run `java -jar minesweeper.jar`

---

## License

MIT — free to use, modify, and share.
