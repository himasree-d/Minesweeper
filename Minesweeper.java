import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;


enum CellState { CLOSED, OPEN, MINE_HIT, MINE_REVEALED, WRONG_FLAG }

public class Minesweeper extends JFrame {

    // ─────────────────────────────  Constants  ─────────────────────────────

    private static final String SAVE_FILE   = "minesweeper_save.dat";
    private static final String PREFS_NODE  = "com.minesweeper";

    // Cell states stored in flagGrid
    private static final int FLAG_NONE  = 0;
    private static final int FLAG_FLAG  = 1;
    private static final int FLAG_QUEST = 2;

    // ─────────────────────────────  Difficulty  ────────────────────────────

    enum Difficulty {
        BEGINNER    ("Beginner",     8, 8,  10),
        INTERMEDIATE("Intermediate", 16,16, 40),
        EXPERT      ("Expert",       30,16, 99);

        final String label;
        final int cols, rows, mines;
        Difficulty(String l, int c, int r, int m){ label=l; cols=c; rows=r; mines=m; }
    }

    // ─────────────────────────────  Themes  ────────────────────────────────

    enum Theme {
        CLASSIC("Classic",
                new Color(189,189,189), new Color(100,149,237),
                new Color(220,220,220), new Color(240,240,240),
                new Color(50,50,50),    new Color(25,50,120)),

        DARK("Dark",
             new Color(45,45,55),      new Color(60,80,160),
             new Color(35,35,45),      new Color(55,55,65),
             new Color(230,230,230),   new Color(20,20,28)),

        PASTEL("Pastel",
               new Color(198,226,255), new Color(144,190,224),
               new Color(220,240,255), new Color(255,255,255),
               new Color(50,50,80),    new Color(180,210,240));

        final String  label;
        final Color   cellClosed, cellClosedHover,
                      cellOpen,   cellOpenAlt,
                      textColor,  background;

        Theme(String l, Color cc, Color ch, Color co, Color ca, Color tc, Color bg){
            label=l; cellClosed=cc; cellClosedHover=ch;
            cellOpen=co; cellOpenAlt=ca; textColor=tc; background=bg;
        }
    }

    // ──────────────────────────  Number colours  ──────────────────────────

    private static final Color[] NUM_COLORS = {
        Color.BLUE,        new Color(0,128,0),  Color.RED,
        new Color(0,0,128),new Color(128,0,0),  new Color(0,128,128),
        Color.BLACK,       Color.GRAY
    };

    // ─────────────────────────  Instance fields  ──────────────────────────

    private Difficulty difficulty = Difficulty.INTERMEDIATE;
    private Theme      theme      = Theme.CLASSIC;

    private int rows, cols, totalMines;
    private int[][] board;           // -1=mine, 0-8=adjacent count
    private int[][] flagGrid;        // FLAG_NONE / FLAG_FLAG / FLAG_QUEST
    private boolean[][] revealed;
    private boolean gameStarted, gameOver, gameWon;
    private int flagsPlaced, cellsRevealed;
    private boolean firstClick;

    // UI
    private JPanel    gridPanel, topBar;
    private JLabel    minesLabel, timerLabel, statusLabel;
    private JButton   newGameBtn;
    private CellButton[][] cellBtns;

    // Timer
    private Timer     swingTimer;
    private int       elapsedSeconds;

    // Sounds
    private boolean   soundEnabled = true;

    // Preferences for best times
    private Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

    // ─────────────────────────────  Entry point  ──────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Minesweeper game = new Minesweeper();
            game.setVisible(true);
        });
    }

    // ─────────────────────────────  Constructor  ──────────────────────────

    Minesweeper() {
        super("💣  Minesweeper — Enhanced Edition");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        buildMenuBar();
        buildUI();
        newGame();
        pack();
        setLocationRelativeTo(null);
    }

    // ─────────────────────────────  Menu bar  ─────────────────────────────

    private void buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        // Game menu
        JMenu gameMenu = new JMenu("Game");

        JMenuItem newItem = new JMenuItem("New Game  F2");
        newItem.addActionListener(e -> newGame());

        JMenuItem saveItem = new JMenuItem("Save Game");
        saveItem.addActionListener(e -> saveGame());

        JMenuItem loadItem = new JMenuItem("Load Game");
        loadItem.addActionListener(e -> loadGame());

        JMenuItem bestItem = new JMenuItem("Best Times…");
        bestItem.addActionListener(e -> showBestTimes());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        gameMenu.add(newItem);
        gameMenu.addSeparator();
        gameMenu.add(saveItem);
        gameMenu.add(loadItem);
        gameMenu.addSeparator();
        gameMenu.add(bestItem);
        gameMenu.addSeparator();
        gameMenu.add(exitItem);

        // Difficulty menu
        JMenu diffMenu = new JMenu("Difficulty");
        ButtonGroup dg = new ButtonGroup();
        for (Difficulty d : Difficulty.values()) {
            JRadioButtonMenuItem rb = new JRadioButtonMenuItem(d.label,
                    d == difficulty);
            rb.addActionListener(e -> { difficulty = d; newGame(); });
            dg.add(rb); diffMenu.add(rb);
        }

        // Theme menu
        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup tg = new ButtonGroup();
        for (Theme t : Theme.values()) {
            JRadioButtonMenuItem rb = new JRadioButtonMenuItem(t.label,
                    t == theme);
            rb.addActionListener(e -> { theme = t; applyTheme(); });
            tg.add(rb); themeMenu.add(rb);
        }

        // Options menu
        JMenu optMenu = new JMenu("Options");
        JCheckBoxMenuItem soundItem = new JCheckBoxMenuItem("Sound Effects", true);
        soundItem.addActionListener(e -> soundEnabled = soundItem.isSelected());

        JMenuItem hintItem = new JMenuItem("Hint  (H)");
        hintItem.addActionListener(e -> giveHint());

        optMenu.add(soundItem);
        optMenu.addSeparator();
        optMenu.add(hintItem);

        mb.add(gameMenu);
        mb.add(diffMenu);
        mb.add(themeMenu);
        mb.add(optMenu);

        // F2 key binding
        getRootPane().registerKeyboardAction(e -> newGame(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // H key binding
        getRootPane().registerKeyboardAction(e -> giveHint(),
                KeyStroke.getKeyStroke(KeyEvent.VK_H, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setJMenuBar(mb);
    }

    // ─────────────────────────────  UI layout  ────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Top bar ──
        topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new CompoundBorder(
                new LineBorder(Color.GRAY, 1, true),
                new EmptyBorder(6, 12, 6, 12)));

        minesLabel  = new JLabel("💣 30");
        timerLabel  = new JLabel("⏱ 000");
        newGameBtn  = new JButton("🙂");
        statusLabel = new JLabel("", SwingConstants.CENTER);

        Font topFont = new Font("Segoe UI Emoji", Font.BOLD, 18);
        minesLabel.setFont(topFont);
        timerLabel.setFont(topFont);
        newGameBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        newGameBtn.setFocusPainted(false);
        newGameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newGameBtn.setToolTipText("New Game (F2)");
        newGameBtn.addActionListener(e -> newGame());

        topBar.add(minesLabel, BorderLayout.WEST);
        topBar.add(newGameBtn, BorderLayout.CENTER);
        topBar.add(timerLabel, BorderLayout.EAST);

        // ── Grid ──
        gridPanel = new JPanel();
        gridPanel.setBorder(new LineBorder(Color.GRAY, 2, true));

        root.add(topBar,   BorderLayout.NORTH);
        root.add(gridPanel,BorderLayout.CENTER);
        setContentPane(root);

        // Swing timer (1 s tick)
        swingTimer = new Timer(1000, e -> {
            if (!gameOver && !gameWon && gameStarted) {
                elapsedSeconds = Math.min(elapsedSeconds + 1, 999);
                timerLabel.setText("⏱ " + String.format("%03d", elapsedSeconds));
            }
        });
    }

    // ──────────────────────────  New / init game  ──────────────────────────

    private void newGame() {
        rows       = difficulty.rows;
        cols       = difficulty.cols;
        totalMines = difficulty.mines;

        board      = new int[rows][cols];
        flagGrid   = new int[rows][cols];
        revealed   = new boolean[rows][cols];

        gameStarted    = false;
        gameOver       = false;
        gameWon        = false;
        firstClick     = true;
        flagsPlaced    = 0;
        cellsRevealed  = 0;
        elapsedSeconds = 0;

        swingTimer.stop();
        timerLabel.setText("⏱ 000");
        minesLabel.setText("💣 " + String.format("%03d", totalMines));
        newGameBtn.setText("🙂");

        // Rebuild grid panel
        gridPanel.removeAll();
        gridPanel.setLayout(new GridLayout(rows, cols, 1, 1));
        gridPanel.setBackground(Color.GRAY);

        cellBtns = new CellButton[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c2 = 0; c2 < cols; c2++) {
                CellButton btn = new CellButton(r, c2);
                cellBtns[r][c2] = btn;
                gridPanel.add(btn);
            }
        }

        applyTheme();
        pack();
        setLocationRelativeTo(null);
    }

    // ──────────────────────────  Board setup  ──────────────────────────────

    private void placeMines(int safeR, int safeC) {
        Random rand = new Random();
        int placed = 0;
        while (placed < totalMines) {
            int r = rand.nextInt(rows);
            int c = rand.nextInt(cols);
            // Ensure first-click cell and its neighbours are safe
            if (board[r][c] == -1) continue;
            if (Math.abs(r - safeR) <= 1 && Math.abs(c - safeC) <= 1) continue;
            board[r][c] = -1;
            placed++;
        }
        calcNumbers();
    }

    private void calcNumbers() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (board[r][c] == -1) continue;
                int adj = 0;
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r+dr, nc = c+dc;
                        if (nr>=0 && nr<rows && nc>=0 && nc<cols && board[nr][nc]==-1)
                            adj++;
                    }
                board[r][c] = adj;
            }
    }

    // ─────────────────────────  Cell reveal logic  ────────────────────────

    private void reveal(int r, int c) {
        if (r<0||r>=rows||c<0||c>=cols) return;
        if (revealed[r][c] || flagGrid[r][c] == FLAG_FLAG) return;

        // First click: place mines now so first click is always safe
        if (firstClick) {
            firstClick = false;
            gameStarted = true;
            placeMines(r, c);
            swingTimer.start();
        }

        revealed[r][c] = true;
        cellsRevealed++;

        if (board[r][c] == -1) {
            // Hit a mine
            playSound("boom");
            cellBtns[r][c].setState(CellState.MINE_HIT);
            revealAllMines();
            triggerGameOver();
            return;
        }

        playSound("click");
        cellBtns[r][c].setState(CellState.OPEN);

        if (board[r][c] == 0) {
            // Flood fill
            for (int dr=-1; dr<=1; dr++)
                for (int dc=-1; dc<=1; dc++)
                    reveal(r+dr, c+dc);
        }

        checkWin();
    }

    /** Chord: if revealed cell's number equals flagged neighbours, auto-reveal rest */
    private void chord(int r, int c) {
        if (!revealed[r][c] || board[r][c] <= 0) return;
        int flags = 0;
        for (int dr=-1; dr<=1; dr++)
            for (int dc=-1; dc<=1; dc++) {
                int nr=r+dr, nc=c+dc;
                if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&flagGrid[nr][nc]==FLAG_FLAG) flags++;
            }
        if (flags == board[r][c]) {
            for (int dr=-1; dr<=1; dr++)
                for (int dc=-1; dc<=1; dc++) {
                    int nr=r+dr, nc=c+dc;
                    if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&!revealed[nr][nc]&&flagGrid[nr][nc]!=FLAG_FLAG)
                        reveal(nr, nc);
                }
        }
    }

    private void cycleFlag(int r, int c) {
        if (revealed[r][c] || gameOver || gameWon) return;
        flagGrid[r][c] = (flagGrid[r][c] + 1) % 3;
        if (flagGrid[r][c] == FLAG_FLAG)      flagsPlaced++;
        else if (flagGrid[r][c] == FLAG_QUEST) flagsPlaced--;
        minesLabel.setText("💣 " + String.format("%03d", totalMines - flagsPlaced));
        cellBtns[r][c].repaint();
        playSound("flag");
    }

    private void revealAllMines() {
        for (int r=0; r<rows; r++)
            for (int c=0; c<cols; c++) {
                if (board[r][c]==-1 && flagGrid[r][c]!=FLAG_FLAG && !revealed[r][c])
                    cellBtns[r][c].setState(CellState.MINE_REVEALED);
                else if (board[r][c]!=-1 && flagGrid[r][c]==FLAG_FLAG)
                    cellBtns[r][c].setState(CellState.WRONG_FLAG);
            }
    }

    private void checkWin() {
        if (cellsRevealed == (rows * cols) - totalMines) {
            gameWon = true;
            swingTimer.stop();
            // Auto-flag all remaining mines
            for (int r=0; r<rows; r++)
                for (int c=0; c<cols; c++)
                    if (board[r][c]==-1 && flagGrid[r][c]!=FLAG_FLAG) {
                        flagGrid[r][c] = FLAG_FLAG;
                        flagsPlaced++;
                        cellBtns[r][c].repaint();
                    }
            minesLabel.setText("💣 000");
            newGameBtn.setText("😎");
            playSound("win");
            checkBestTime();
            SwingUtilities.invokeLater(() ->
                showWinDialog());
        }
    }

    private void triggerGameOver() {
        gameOver = true;
        swingTimer.stop();
        newGameBtn.setText("😵");
        SwingUtilities.invokeLater(() ->
            showGameOverDialog());
    }

    // ────────────────────────────  Hint system  ───────────────────────────

    private void giveHint() {
        if (gameOver || gameWon || !gameStarted) return;
        // Find a safe unrevealed, unflagged cell that is adjacent to a revealed number
        for (int r=0; r<rows; r++) {
            for (int c=0; c<cols; c++) {
                if (!revealed[r][c] && flagGrid[r][c]==FLAG_NONE && board[r][c]!=-1) {
                    // Check if this cell can be deduced safe by any neighbour
                    if (isClearlySafe(r, c)) {
                        flashHint(r, c);
                        return;
                    }
                }
            }
        }
        JOptionPane.showMessageDialog(this,
                "No obvious hint available. Try flagging suspected mines first.",
                "Hint", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean isClearlySafe(int tr, int tc) {
        for (int dr=-1; dr<=1; dr++)
            for (int dc=-1; dc<=1; dc++) {
                int nr=tr+dr, nc=tc+dc;
                if (nr<0||nr>=rows||nc<0||nc>=cols) continue;
                if (!revealed[nr][nc] || board[nr][nc]<=0) continue;
                // Count flags and unrevealed around this number cell
                int flags=0, unknown=0;
                for (int a=-1; a<=1; a++)
                    for (int b=-1; b<=1; b++) {
                        int ar=nr+a, ac=nc+b;
                        if (ar<0||ar>=rows||ac<0||ac>=cols) continue;
                        if (flagGrid[ar][ac]==FLAG_FLAG) flags++;
                        else if (!revealed[ar][ac]) unknown++;
                    }
                // If flags == number, all other unknowns are safe
                if (flags == board[nr][nc] && unknown > 0) return true;
            }
        return false;
    }

    private void flashHint(int r, int c) {
        CellButton btn = cellBtns[r][c];
        Color orig = theme.cellClosed;
        Timer t = new Timer(300, null);
        final int[] count = {0};
        t.addActionListener(e -> {
            count[0]++;
            btn.setHintHighlight(count[0] % 2 == 1);
            btn.repaint();
            if (count[0] >= 6) { t.stop(); btn.setHintHighlight(false); btn.repaint(); }
        });
        t.start();
    }

    // ──────────────────────────  Best times  ──────────────────────────────

    private void checkBestTime() {
        String key = "best_" + difficulty.name();
        int best = prefs.getInt(key, Integer.MAX_VALUE);
        if (elapsedSeconds < best) {
            prefs.putInt(key, elapsedSeconds);
        }
    }

    private void showBestTimes() {
        StringBuilder sb = new StringBuilder("<html><h3>Best Times</h3><table>");
        for (Difficulty d : Difficulty.values()) {
            int t = prefs.getInt("best_" + d.name(), -1);
            String val = t < 0 ? "—" : t + "s";
            sb.append("<tr><td><b>").append(d.label).append(":</b></td>")
              .append("<td>&nbsp;").append(val).append("</td></tr>");
        }
        sb.append("</table></html>");
        int opt = JOptionPane.showOptionDialog(this, sb.toString(),
                "Best Times", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{"OK","Reset Times"}, "OK");
        if (opt == 1) {
            for (Difficulty d : Difficulty.values())
                prefs.remove("best_" + d.name());
        }
    }

    // ──────────────────────────  Save / Load  ─────────────────────────────

    private void saveGame() {
        if (gameOver || gameWon || !gameStarted) {
            JOptionPane.showMessageDialog(this, "No active game to save.");
            return;
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(SAVE_FILE))) {
            oos.writeObject(difficulty);
            oos.writeObject(board);
            oos.writeObject(flagGrid);
            oos.writeObject(revealed);
            oos.writeInt(elapsedSeconds);
            oos.writeInt(flagsPlaced);
            oos.writeInt(cellsRevealed);
            JOptionPane.showMessageDialog(this, "Game saved ✓");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadGame() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "No saved game found.");
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(SAVE_FILE))) {
            difficulty     = (Difficulty) ois.readObject();
            board          = (int[][])    ois.readObject();
            flagGrid       = (int[][])    ois.readObject();
            revealed       = (boolean[][])ois.readObject();
            elapsedSeconds = ois.readInt();
            flagsPlaced    = ois.readInt();
            cellsRevealed  = ois.readInt();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        rows       = difficulty.rows;
        cols       = difficulty.cols;
        totalMines = difficulty.mines;
        gameStarted = true;
        firstClick  = false;
        gameOver    = false;
        gameWon     = false;

        swingTimer.stop();
        timerLabel.setText("⏱ " + String.format("%03d", elapsedSeconds));
        minesLabel.setText("💣 " + String.format("%03d", totalMines - flagsPlaced));
        newGameBtn.setText("🙂");

        // Rebuild grid
        gridPanel.removeAll();
        gridPanel.setLayout(new GridLayout(rows, cols, 1, 1));
        gridPanel.setBackground(Color.GRAY);
        cellBtns = new CellButton[rows][cols];
        for (int r=0; r<rows; r++)
            for (int c=0; c<cols; c++) {
                CellButton btn = new CellButton(r, c);
                if (revealed[r][c]) {
                    btn.setState(board[r][c]==-1
                            ? CellState.MINE_HIT
                            : CellState.OPEN);
                }
                cellBtns[r][c] = btn;
                gridPanel.add(btn);
            }

        applyTheme();
        swingTimer.start();
        pack();
        setLocationRelativeTo(null);
    }

    // ──────────────────────────  Dialog helpers  ──────────────────────────

    private void showGameOverDialog() {
        String[] opts = {"New Game","Quit"};
        int ch = JOptionPane.showOptionDialog(this,
                "💥  You hit a mine!\nBetter luck next time.",
                "Game Over", JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, opts, opts[0]);
        if (ch == 0) newGame();
        else System.exit(0);
    }

    private void showWinDialog() {
        String key  = "best_" + difficulty.name();
        int best    = prefs.getInt(key, Integer.MAX_VALUE);
        boolean newRecord = elapsedSeconds <= best;
        String msg = "🎉  You won in " + elapsedSeconds + " seconds!"
                + (newRecord ? "\n🏆  New personal best!" : "");
        String[] opts = {"New Game","Quit"};
        int ch = JOptionPane.showOptionDialog(this, msg,
                "You Win!", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
        if (ch == 0) newGame();
        else System.exit(0);
    }

    // ──────────────────────────  Theme application  ───────────────────────

    private void applyTheme() {
        getContentPane().setBackground(theme.background);
        topBar.setBackground(theme.background);
        topBar.setBorder(new CompoundBorder(
                new LineBorder(theme.cellClosed.darker(), 1, true),
                new EmptyBorder(6,12,6,12)));
        minesLabel.setForeground(theme.textColor);
        timerLabel.setForeground(theme.textColor);
        gridPanel.setBackground(theme.background.darker());
        if (cellBtns != null)
            for (CellButton[] row : cellBtns)
                for (CellButton btn : row) btn.repaint();
        repaint();
    }

    // ─────────────────────────────  Sound  ────────────────────────────────

    /** Synthesise simple PCM tones — no external audio files needed */
    private void playSound(String type) {
        if (!soundEnabled) return;
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(44100, 8, 1, true, false);
                byte[] buf;
                switch (type) {
                    case "click":  buf = tone(440, 60,  0.3);   break;
                    case "flag":   buf = tone(600, 80,  0.25);  break;
                    case "boom":   buf = noise(300,     0.9);   break;
                    case "win":    buf = fanfare();              break;
                    default:       return;
                }
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    private byte[] tone(double freq, int millis, double vol) {
        int samples = (int)(44100 * millis / 1000.0);
        byte[] buf = new byte[samples];
        for (int i=0; i<samples; i++) {
            double env = 1.0 - (double)i/samples; // linear decay
            buf[i] = (byte)(Math.sin(2*Math.PI*freq*i/44100)*127*vol*env);
        }
        return buf;
    }

    private byte[] noise(int millis, double vol) {
        int samples = (int)(44100 * millis / 1000.0);
        byte[] buf = new byte[samples];
        Random rand = new Random();
        for (int i=0; i<samples; i++) {
            double env = 1.0 - (double)i/samples;
            buf[i] = (byte)((rand.nextDouble()*2-1)*127*vol*env);
        }
        return buf;
    }

    private byte[] fanfare() {
        int[] notes = {523,659,784,1047};
        int dur = 100;
        int total = 44100 * notes.length * dur / 1000;
        byte[] buf = new byte[total];
        int idx = 0;
        for (int freq : notes) {
            byte[] t = tone(freq, dur, 0.5);
            System.arraycopy(t, 0, buf, idx, t.length);
            idx += t.length;
        }
        return buf;
    }

    // ══════════════════════════  CellButton inner class  ══════════════════

    private class CellButton extends JButton implements MouseListener {

        final int row, col;
        CellState state = CellState.CLOSED;
        boolean hovered = false;
        boolean hintHighlight = false;

        CellButton(int r, int c) {
            this.row = r; this.col = c;
            setPreferredSize(new Dimension(32, 32));
            setOpaque(true);
            setBorderPainted(false);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(this);
        }

        void setState(CellState s) { this.state = s; repaint(); }
        void setHintHighlight(boolean h) { this.hintHighlight = h; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            switch (state) {
                case CLOSED: {
                    Color base = hintHighlight ? new Color(100,220,100)
                               : hovered       ? theme.cellClosedHover
                               :                 theme.cellClosed;
                    g2.setColor(base);
                    g2.fillRoundRect(1, 1, w-2, h-2, 4, 4);
                    // 3-D bevel
                    g2.setColor(base.brighter());
                    g2.drawLine(1,1,w-2,1); g2.drawLine(1,1,1,h-2);
                    g2.setColor(base.darker().darker());
                    g2.drawLine(1,h-1,w-1,h-1); g2.drawLine(w-1,1,w-1,h-1);

                    // Flag or question mark
                    if (flagGrid[row][col] == FLAG_FLAG) {
                        drawEmoji(g2, "🚩", w, h, 14);
                    } else if (flagGrid[row][col] == FLAG_QUEST) {
                        g2.setColor(new Color(0,100,200));
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString("?", (w-fm.stringWidth("?"))/2,
                                (h+fm.getAscent()-fm.getDescent())/2);
                    }
                    break;
                }
                case OPEN: {
                    // Checkerboard for revealed cells
                    boolean alt = (row + col) % 2 == 0;
                    g2.setColor(alt ? theme.cellOpen : theme.cellOpenAlt);
                    g2.fillRect(0, 0, w, h);
                    int n = board[row][col];
                    if (n > 0) {
                        g2.setColor(NUM_COLORS[n-1]);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        FontMetrics fm = g2.getFontMetrics();
                        String s = String.valueOf(n);
                        g2.drawString(s, (w-fm.stringWidth(s))/2,
                                (h+fm.getAscent()-fm.getDescent())/2);
                    }
                    break;
                }
                case MINE_HIT: {
                    g2.setColor(new Color(255, 60, 60));
                    g2.fillRoundRect(0, 0, w, h, 4, 4);
                    drawEmoji(g2, "💥", w, h, 16);
                    break;
                }
                case MINE_REVEALED: {
                    g2.setColor(new Color(80, 80, 80));
                    g2.fillRoundRect(0, 0, w, h, 4, 4);
                    drawEmoji(g2, "💣", w, h, 14);
                    break;
                }
                case WRONG_FLAG: {
                    g2.setColor(new Color(255, 180, 180));
                    g2.fillRoundRect(0, 0, w, h, 4, 4);
                    drawEmoji(g2, "❌", w, h, 14);
                    break;
                }
            }
            g2.dispose();
        }

        private void drawEmoji(Graphics2D g2, String emoji, int w, int h, int size) {
            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, size));
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(emoji)) / 2;
            int y = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(emoji, x, y);
        }

        // ── Mouse events ──

        public void mouseClicked(MouseEvent e) {
            if (gameOver || gameWon) return;
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (revealed[row][col]) chord(row, col);
                else reveal(row, col);
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                cycleFlag(row, col);
            } else if (e.getButton() == MouseEvent.BUTTON2) {
                chord(row, col);
            }
        }

        public void mouseEntered(MouseEvent e) { hovered=true;  repaint(); }
        public void mouseExited (MouseEvent e) { hovered=false; repaint(); }
        public void mousePressed (MouseEvent e) {}
        public void mouseReleased(MouseEvent e) {}
    }
}
