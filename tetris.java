import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class tetris extends JFrame {
    private TetrisPanel gamePanel;

    public tetris() {
        setTitle("Tetris - Advanced");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        gamePanel = new TetrisPanel();
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new tetris());
    }
}

class TetrisPanel extends JPanel {
    private static final int WIDTH = 700;
    private static final int HEIGHT = 950;
    private static final int BLOCK_SIZE = 30;
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 30;
    private static final int PADDING = 40;
    private static final int GRID_OFFSET_X = 175;
    private static final int GRID_OFFSET_Y = 10;
    private static final int PREFERRED_WIDTH = GRID_OFFSET_X + (GRID_WIDTH * BLOCK_SIZE) + PADDING + 10;

    private int[][] grid;
    private Tetromino currentTetromino;
    private Tetromino nextTetromino;
    private int score;
    private int lines;
    private int level;
    private boolean gameOver;
    private boolean gamePaused;
    private Timer gameTimer;
    private float renderPieceY;
    private float targetPieceY;
    private float renderPieceX;
    private float targetPieceX;
    private Timer renderTimer;
    private long colorCycleTime = 0;

    private List<Integer> highScores;
    private static final String HIGH_SCORES_FILE = "highscores.txt";
    private List<Tetromino> holdQueue; 
    private Tetromino heldTetromino;
    private boolean canSwap;
    private int comboCount;
    private long gameOverImageTime = 0;
    private static final long IMAGE_DISPLAY_TIME = 2000;
    private BufferedImage gameOverImage = null;
    private boolean imageLoadAttempted = false;
    private int guiDesign = 0; // 0 = Default, 1 = Neon, 2 = Retro, 3 = Windows 95
    private Rectangle[] designButtons = new Rectangle[4];
    private String[] designNames = {"Default", "Sunset", "Retro", "Win95"};
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTONS_START_Y = 720;
    private static final int BUTTON_SPACING = 10;

    public TetrisPanel() {
        setPreferredSize(new Dimension(PREFERRED_WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocus();

        grid = new int[GRID_HEIGHT][GRID_WIDTH];
        score = 0;
        lines = 0;
        level = 1;
        gameOver = false;
        gamePaused = false;
        highScores = new ArrayList<>();
        holdQueue = new ArrayList<>();
        heldTetromino = null;
        canSwap = true;
        comboCount = 0;
        loadHighScores();

        currentTetromino = new Tetromino();
        nextTetromino = new Tetromino();
        
        // Initialize design buttons - centered
        int totalButtonWidth = 4 * BUTTON_WIDTH + 3 * BUTTON_SPACING;
        int startX = (PREFERRED_WIDTH - totalButtonWidth) / 2;
        for (int i = 0; i < 4; i++) {
            designButtons[i] = new Rectangle(startX + i * (BUTTON_WIDTH + BUTTON_SPACING), BUTTONS_START_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        }
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (int i = 0; i < designButtons.length; i++) {
                    if (designButtons[i].contains(e.getPoint())) {
                        guiDesign = i;
                        repaint();
                    }
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_P) {
                    gamePaused = !gamePaused;
                    repaint();
                    return;
                }
                
                if (e.getKeyCode() == KeyEvent.VK_D) {
                    guiDesign = (guiDesign + 1) % 4;
                    repaint();
                    return;
                }

                if (gamePaused) return;

                if (!gameOver) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                            moveLeft();
                            break;
                        case KeyEvent.VK_RIGHT:
                            moveRight();
                            break;
                        case KeyEvent.VK_DOWN:
                            hardDrop();
                            break;
                        case KeyEvent.VK_UP:
                            rotate();
                            break;
                        case KeyEvent.VK_C:
                            holdPiece();
                            break;
                        case KeyEvent.VK_SPACE:
                            softDrop();
                            break;
                    }
                    repaint();
                } else {
                    if (e.getKeyCode() == KeyEvent.VK_R) {
                        restartGame();
                    }
                }
            }
        });

        gameTimer = new Timer(Math.max(100, 300 - (level * 30)), e -> {
            if (!gameOver && !gamePaused) {
                moveDown(false);
            }
        });
        gameTimer.start();

        renderPieceY = currentTetromino.y * BLOCK_SIZE;
        targetPieceY = renderPieceY;
        renderPieceX = currentTetromino.x * BLOCK_SIZE;
        targetPieceX = renderPieceX;
        renderTimer = new Timer(16, e -> {
            colorCycleTime += 16; 
            if (!gameOver && !gamePaused) {
                float frameDelay = renderTimer.getDelay();
                float gameDelay = Math.max(1, gameTimer.getDelay());
                float step = BLOCK_SIZE * (frameDelay / gameDelay);

                if (Math.abs(renderPieceY - targetPieceY) > 0.5f) {
                    if (renderPieceY < targetPieceY) {
                        renderPieceY = Math.min(renderPieceY + step, targetPieceY);
                    } else {
                        renderPieceY = Math.max(renderPieceY - step, targetPieceY);
                    }
                }
                if (Math.abs(renderPieceX - targetPieceX) > 0.5f) {
                    if (renderPieceX < targetPieceX) {
                        renderPieceX = Math.min(renderPieceX + step, targetPieceX);
                    } else {
                        renderPieceX = Math.max(renderPieceX - step, targetPieceX);
                    }
                }
            }
            repaint();
        });
        renderTimer.start();
    }

    private void bild() {
        if(gameOver) {
            if (!imageLoadAttempted) {
                imageLoadAttempted = true;
                try {
                    File file = new File("0H:\\Desktop\\kimera-evo38.jpg");
                    gameOverImage = ImageIO.read(file);
                    if (gameOverImage != null) {
                        System.out.println("Bild erfolgreich geladen: " + gameOverImage.getWidth() + "x" + gameOverImage.getHeight());
                    } else {
                        System.err.println("Bild ist null nach ImageIO.read()");
                    }
                } catch (IOException e) {
                    System.err.println("Fehler beim Laden des Bildes: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            gameOverImageTime = System.currentTimeMillis();
        }
    }

    private void holdPiece() {
        if (!canSwap) return;

        if (heldTetromino == null) {
            heldTetromino = currentTetromino;
            currentTetromino = nextTetromino;
            nextTetromino = new Tetromino();
        } else {
            Tetromino temp = currentTetromino;
            currentTetromino = heldTetromino;
            heldTetromino = temp;
        }
        currentTetromino.x = 3;
        currentTetromino.y = 0;
        canSwap = false;
        renderPieceY = currentTetromino.y * BLOCK_SIZE;
        targetPieceY = renderPieceY;
        renderPieceX = currentTetromino.x * BLOCK_SIZE;
        targetPieceX = renderPieceX;
    }

    private void softDrop() {
        if (canMove(currentTetromino.x, currentTetromino.y + 1, currentTetromino.shape)) {
            currentTetromino.y++;
            targetPieceY = currentTetromino.y * BLOCK_SIZE;
            score += 1;
        }
    }

    private void loadHighScores() {
        try (BufferedReader reader = new BufferedReader(new FileReader(HIGH_SCORES_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    highScores.add(Integer.parseInt(line));
                } catch (NumberFormatException e) {}
            }
        } catch (IOException e) {}
    }

    private void saveHighScore(int s) {
        highScores.add(s);
        highScores.sort((a, b) -> b.compareTo(a));
        if (highScores.size() > 10) {
            highScores.remove(highScores.size() - 1);
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(HIGH_SCORES_FILE))) {
            for (int score : highScores) {
                writer.println(score);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void moveLeft() {
        if (canMove(currentTetromino.x - 1, currentTetromino.y, currentTetromino.shape)) {
            currentTetromino.x--;
            targetPieceX = currentTetromino.x * BLOCK_SIZE;
            renderPieceX = targetPieceX;
        }
    }

    private void moveRight() {
        if (canMove(currentTetromino.x + 1, currentTetromino.y, currentTetromino.shape)) {
            currentTetromino.x++;
            targetPieceX = currentTetromino.x * BLOCK_SIZE;
            renderPieceX = targetPieceX;
        }
    }

    private void moveDown(boolean snap) {
        if (canMove(currentTetromino.x, currentTetromino.y + 1, currentTetromino.shape)) {
            currentTetromino.y++;
            targetPieceY = currentTetromino.y * BLOCK_SIZE;
            if (snap) {
                renderPieceY = targetPieceY;
            }
        } else {
            placeTetromino();
            clearLines();
            spawnNewTetromino();
        }
    }

    private void hardDrop() {
        int dropDistance = 0;
        while (canMove(currentTetromino.x, currentTetromino.y + 1, currentTetromino.shape)) {
            currentTetromino.y++;
            dropDistance++;
        }
        score += dropDistance * 2;
        targetPieceY = currentTetromino.y * BLOCK_SIZE;
        renderPieceY = targetPieceY;
        targetPieceX = currentTetromino.x * BLOCK_SIZE;
        renderPieceX = targetPieceX;
        placeTetromino();
        clearLines();
        spawnNewTetromino();
    }

    private void rotate() {
        int[][] rotated = rotateShape(copyShape(currentTetromino.shape));
        if (canMove(currentTetromino.x, currentTetromino.y, rotated)) {
            currentTetromino.shape = rotated;
            targetPieceX = currentTetromino.x * BLOCK_SIZE;
            targetPieceY = currentTetromino.y * BLOCK_SIZE;
        }
    }

    private int[][] copyShape(int[][] shape) {
        int[][] copy = new int[shape.length][shape[0].length];
        for (int i = 0; i < shape.length; i++) {
            System.arraycopy(shape[i], 0, copy[i], 0, shape[i].length);
        }
        return copy;
    }

    private int[][] rotateShape(int[][] shape) {
        int rows = shape.length;
        int cols = shape[0].length;
        int[][] rotated = new int[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                rotated[j][rows - 1 - i] = shape[i][j];
            }
        }
        return rotated;
    }

    private boolean canMove(int x, int y, int[][] shape) {
        for (int i = 0; i < shape.length; i++) {
            for (int j = 0; j < shape[0].length; j++) {
                if (shape[i][j] == 1) {
                    int newX = x + j;
                    int newY = y + i;
                    if (newX < 0 || newX >= GRID_WIDTH || newY >= GRID_HEIGHT) {
                        return false;
                    }
                    if (newY >= 0 && grid[newY][newX] != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void placeTetromino() {
        for (int i = 0; i < currentTetromino.shape.length; i++) {
            for (int j = 0; j < currentTetromino.shape[0].length; j++) {
                if (currentTetromino.shape[i][j] == 1) {
                    int x = currentTetromino.x + j;
                    int y = currentTetromino.y + i;
                    if (y >= 0 && y < GRID_HEIGHT && x >= 0 && x < GRID_WIDTH) {
                        grid[y][x] = currentTetromino.color;
                    } else if (y < 0) {
                        gameOver = true;
                    }
                }
            }
        }
        if (gameOver) {
            saveHighScore(score);
            loadHighScores();
            bild();
        }
    }

    private void clearLines() {
        int linesCleared = 0;
        for (int i = GRID_HEIGHT - 1; i >= 0; i--) {
            boolean fullLine = true;
            for (int j = 0; j < GRID_WIDTH; j++) {
                if (grid[i][j] == 0) {
                    fullLine = false;
                    break;
                }
            }
            if (fullLine) {
                linesCleared++;
                for (int k = i; k > 0; k--) {
                    System.arraycopy(grid[k - 1], 0, grid[k], 0, GRID_WIDTH);
                }
                System.arraycopy(new int[GRID_WIDTH], 0, grid[0], 0, GRID_WIDTH);
                i++;
            }
        }

        if (linesCleared > 0) {
            lines += linesCleared;
            comboCount++;
            int baseScore = linesCleared == 4 ? 800 : linesCleared * 100;
            score += baseScore * level * comboCount;
            level = Math.min(20, 1 + (lines / 10));
            gameTimer.setDelay(Math.max(100, 300 - (level * 30)));
        } else {
            comboCount = 0;
        }
    }

    private void spawnNewTetromino() {
        currentTetromino = nextTetromino;
        nextTetromino = new Tetromino();
        canSwap = true;
        renderPieceY = currentTetromino.y * BLOCK_SIZE;
        targetPieceY = renderPieceY;
        renderPieceX = currentTetromino.x * BLOCK_SIZE;
        targetPieceX = renderPieceX;
        if (!canMove(currentTetromino.x, currentTetromino.y, currentTetromino.shape)) {
            gameOver = true;
        }
    }

    private void restartGame() {
        grid = new int[GRID_HEIGHT][GRID_WIDTH];
        score = 0;
        lines = 0;
        level = 1;
        gameOver = false;
        gamePaused = false;
        heldTetromino = null;
        canSwap = true;
        comboCount = 0;
        gameOverImageTime = 0;
        currentTetromino = new Tetromino();
        nextTetromino = new Tetromino();
        renderPieceY = currentTetromino.y * BLOCK_SIZE;
        targetPieceY = renderPieceY;
        renderPieceX = currentTetromino.x * BLOCK_SIZE;
        targetPieceX = renderPieceX;
        gameTimer.setDelay(300);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw background based on design
        if (guiDesign == 3) {
            g2d.setColor(new Color(192, 192, 192));
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
        } else {
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
        }
        
        drawGrid(g2d);
        drawGhostPiece(g2d);
        drawTetromino(g2d, currentTetromino);
        drawScore(g2d);
        drawNextPiece(g2d);
        drawHeldPiece(g2d);
        drawStats(g2d);

        if (gamePaused) {
            drawPaused(g2d);
        }
        if (gameOver) {
            drawGameOver(g2d);
        }
    }

    private void drawStats(Graphics2D g) {
        if (guiDesign == 0) {
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 0));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Lines: " + lines, 10, 250);
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 1));
            g.drawString("Level: " + level, 10, 280);
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 2));
            g.drawString("Combo: " + comboCount, 10, 310);
        } else if (guiDesign == 1) {
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.setColor(new Color(255, 165, 0));
            g.drawString("Lines: " + lines, 10, 250);
            g.setColor(new Color(255, 100, 0));
            g.drawString("Level: " + level, 10, 280);
            g.setColor(new Color(255, 69, 0));
            g.drawString("Combo: " + comboCount, 10, 310);
        } else if (guiDesign == 2) {
            g.setFont(new Font("Courier New", Font.PLAIN, 14));
            g.setColor(new Color(200, 200, 0));
            g.drawString("Lines: " + lines, 10, 250);
            g.drawString("Level: " + level, 10, 280);
            g.drawString("Combo: " + comboCount, 10, 310);
        } else {
            // Windows 95 style
            g.setFont(new Font("MS Sans Serif", Font.PLAIN, 11));
            g.setColor(new Color(0, 0, 0));
            g.drawString("Lines: " + lines, 10, 250);
            g.drawString("Level: " + level, 10, 280);
            g.drawString("Combo: " + comboCount, 10, 310);
        }
    }

    private void drawPaused(Graphics2D g) {
        if (guiDesign == 0) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 0));
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString("PAUSED", 250, 300);
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 1));
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString("Press P to Resume | Press D to Change Design", 220, 350);
        } else if (guiDesign == 1) {
            g.setColor(new Color(40, 20, 10, 180));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(new Color(255, 100, 0, 150));
            g.fillRect(200, 260, 300, 100);
            g.setColor(new Color(255, 165, 0));
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString("PAUSED", 250, 320);
            g.setColor(new Color(255, 200, 50));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString(">> Press P to Resume | Press D to Change Design <<", 180, 380);
        } else if (guiDesign == 2) {
            g.setColor(new Color(50, 50, 50, 180));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(new Color(200, 200, 0));
            g.setStroke(new BasicStroke(3));
            g.drawRect(150, 250, 400, 150);
            g.setFont(new Font("Courier New", Font.BOLD, 40));
            g.drawString("PAUSED", 280, 320);
            g.setFont(new Font("Courier New", Font.PLAIN, 14));
            g.drawString("[P] RESUME  [D] DESIGN", 260, 380);
        } else {
            // Windows 95 style pause
            g.setColor(new Color(192, 192, 192, 200));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            // Draw beveled window frame
            g.setColor(new Color(255, 255, 255));
            g.setStroke(new BasicStroke(2));
            g.drawRect(150, 200, 400, 250);
            g.setColor(new Color(128, 128, 128));
            g.drawLine(151, 200, 151, 450);
            g.drawLine(150, 200, 550, 200);
            
            g.setColor(new Color(0, 0, 170));
            g.fillRect(150, 200, 400, 25);
            g.setColor(new Color(255, 255, 255));
            g.setFont(new Font("MS Sans Serif", Font.BOLD, 14));
            g.drawString("PAUSED", 300, 220);
            
            g.setColor(new Color(0, 0, 0));
            g.setFont(new Font("MS Sans Serif", Font.PLAIN, 12));
            g.drawString("P = Resume  |  D = Design", 220, 300);
        }
    }

    private Color getDesignColor(int colorIndex) {
        if (guiDesign == 0) {
            return ColorCycler.getPastelRainbowColor(colorCycleTime, colorIndex);
        } else if (guiDesign == 1) {
            Color[] sunsetColors = {
                new Color(255, 165, 0),  // Orange
                new Color(255, 100, 0),  // Dark Orange
                new Color(255, 69, 0),   // Red-Orange
                new Color(220, 80, 0),   // Deep Orange
                new Color(255, 140, 0),  // Light Orange
                new Color(200, 60, 0),   // Rust
                new Color(255, 110, 0),  // Golden Orange
                new Color(180, 50, 0)    // Dark Rust
            };
            return sunsetColors[colorIndex % sunsetColors.length];
        } else if (guiDesign == 2) {
            Color[] retroColors = {
                new Color(200, 200, 0),  // Yellow
                new Color(200, 0, 0),    // Red
                new Color(0, 200, 200),  // Cyan
                new Color(200, 100, 0),  // Orange
                new Color(200, 0, 200),  // Magenta
                new Color(0, 200, 0),    // Green
                new Color(200, 200, 0),  // Yellow
                new Color(200, 0, 0)     // Red
            };
            return retroColors[colorIndex % retroColors.length];
        } else {
            // Windows 95 style colors
            Color[] win95Colors = {
                new Color(0, 0, 170),    // Dark Blue
                new Color(0, 170, 170),  // Cyan
                new Color(170, 0, 0),    // Red
                new Color(170, 85, 0),   // Brown
                new Color(170, 0, 170),  // Magenta
                new Color(0, 170, 0),    // Green
                new Color(170, 170, 0),  // Yellow
                new Color(0, 0, 255)     // Bright Blue
            };
            return win95Colors[colorIndex % win95Colors.length];
        }
    }

    private void drawGrid(Graphics2D g) {
        Color bgColor, gridColor, borderColor;
        
        if (guiDesign == 0) {
            bgColor = Color.BLACK;
            gridColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 3);
            borderColor = gridColor;
        } else if (guiDesign == 1) {
            bgColor = new Color(40, 20, 10);
            gridColor = new Color(150, 70, 20);
            borderColor = new Color(255, 165, 0);
        } else if (guiDesign == 2) {
            bgColor = new Color(30, 30, 30);
            gridColor = new Color(100, 100, 0);
            borderColor = new Color(200, 200, 0);
        } else {
            bgColor = new Color(212, 208, 200);  // Windows 95 light gray
            gridColor = new Color(128, 128, 128);
            borderColor = new Color(0, 0, 170);
        }
        
        g.setColor(bgColor);
        g.fillRect(GRID_OFFSET_X - 2, GRID_OFFSET_Y - 2, GRID_WIDTH * BLOCK_SIZE + 4, GRID_HEIGHT * BLOCK_SIZE + 4);

        g.setColor(gridColor);
        int dotSize = Math.max(2, BLOCK_SIZE / 8);
        for (int i = 0; i < GRID_HEIGHT; i++) {
            for (int j = 0; j < GRID_WIDTH; j++) {
                int cx = GRID_OFFSET_X + j * BLOCK_SIZE + BLOCK_SIZE / 2 - dotSize / 2;
                int cy = GRID_OFFSET_Y + i * BLOCK_SIZE + BLOCK_SIZE / 2 - dotSize / 2;
                g.fillRect(cx, cy, dotSize, dotSize);
            }
        }

        for (int i = 0; i < GRID_HEIGHT; i++) {
            for (int j = 0; j < GRID_WIDTH; j++) {
                if (grid[i][j] != 0) {
                    Color blockColor = getDesignColor(grid[i][j]);
                    drawBlockAtCell(g, j, i, blockColor);
                } else {
                    Color gridlineColor;
                    if (guiDesign == 0) gridlineColor = new Color(50, 50, 50);
                    else if (guiDesign == 1) gridlineColor = new Color(100, 50, 20);
                    else if (guiDesign == 2) gridlineColor = new Color(80, 80, 30);
                    else gridlineColor = new Color(160, 160, 160);
                    g.setColor(gridlineColor);
                    g.drawRect(GRID_OFFSET_X + j * BLOCK_SIZE, GRID_OFFSET_Y + i * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                }
            }
        }

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(12, BLOCK_SIZE / 2)));
        g.setColor(borderColor);
        for (int i = 0; i < GRID_HEIGHT; i++) {
            int y = GRID_OFFSET_Y + i * BLOCK_SIZE + BLOCK_SIZE / 2 + 6;
            g.drawString("<", GRID_OFFSET_X - 28, y);
            g.drawString(">", GRID_OFFSET_X + GRID_WIDTH * BLOCK_SIZE + 16, y);
        }
        
        // Draw border with Windows 95 beveled effect for Win95 design
        if (guiDesign == 3) {
            g.setColor(new Color(255, 255, 255));
            g.setStroke(new BasicStroke(2));
            g.drawRect(GRID_OFFSET_X - 3, GRID_OFFSET_Y - 3, GRID_WIDTH * BLOCK_SIZE + 6, GRID_HEIGHT * BLOCK_SIZE + 6);
            g.setColor(new Color(128, 128, 128));
            g.drawRect(GRID_OFFSET_X - 2, GRID_OFFSET_Y - 2, GRID_WIDTH * BLOCK_SIZE + 4, GRID_HEIGHT * BLOCK_SIZE + 4);
        } else {
            g.setColor(borderColor);
            g.setStroke(new BasicStroke(2));
            g.drawRect(GRID_OFFSET_X - 2, GRID_OFFSET_Y - 2, GRID_WIDTH * BLOCK_SIZE + 4, GRID_HEIGHT * BLOCK_SIZE + 4);
        }
    }

    private void drawTetromino(Graphics2D g, Tetromino t) {
        for (int i = 0; i < t.shape.length; i++) {
            for (int j = 0; j < t.shape[0].length; j++) {
                if (t.shape[i][j] == 1) {
                    int x = GRID_OFFSET_X + (int) renderPieceX + j * BLOCK_SIZE;
                    int y = GRID_OFFSET_Y + (int) renderPieceY + i * BLOCK_SIZE;
                    Color blockColor = getDesignColor(t.color);
                    drawBlockAtPixel(g, x, y, blockColor);
                }
            }
        }
    }

    private void drawGhostPiece(Graphics2D g) {
        // Calculate where the piece will land
        Tetromino ghost = new Tetromino();
        ghost.shape = copyShape(currentTetromino.shape);
        ghost.x = currentTetromino.x;
        ghost.y = currentTetromino.y;

        // Drop the ghost piece to the bottom
        while (canMove(ghost.x, ghost.y + 1, ghost.shape)) {
            ghost.y++;
        }

        // Draw ghost piece with semi-transparency
        for (int i = 0; i < ghost.shape.length; i++) {
            for (int j = 0; j < ghost.shape[0].length; j++) {
                if (ghost.shape[i][j] == 1) {
                    int cellX = ghost.x + j;
                    int cellY = ghost.y + i;

                    if (cellX >= 0 && cellX < GRID_WIDTH && cellY >= 0 && cellY < GRID_HEIGHT) {
                        int x = GRID_OFFSET_X + cellX * BLOCK_SIZE;
                        int y = GRID_OFFSET_Y + cellY * BLOCK_SIZE;

                        // Draw semi-transparent ghost with design-aware color
                        Color baseColor = getDesignColor(currentTetromino.color);
                        Color ghostColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 70);
                        g.setColor(ghostColor);
                        g.fillRect(x, y, BLOCK_SIZE, BLOCK_SIZE);

                        g.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 100));
                        g.setStroke(new BasicStroke(2));
                        g.drawRect(x, y, BLOCK_SIZE, BLOCK_SIZE);
                    }
                }
            }
        }
    }

    private void drawBlockAtCell(Graphics2D g, int cellX, int cellY, Color base) {
        int x = GRID_OFFSET_X + cellX * BLOCK_SIZE;
        int y = GRID_OFFSET_Y + cellY * BLOCK_SIZE;
        drawBlockAtPixel(g, x, y, base);
    }

    private void drawBlockAtPixel(Graphics2D g, int x, int y, Color base) {
        Color darker = base.darker();
        Color brighter = base.brighter();

        g.setColor(darker);
        g.fillRect(x + 1, y + 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2);

        int pad = Math.max(2, BLOCK_SIZE / 6);
        g.setColor(brighter);
        g.fillRect(x + pad, y + pad, BLOCK_SIZE - pad * 2, BLOCK_SIZE - pad * 2);

        g.setColor(new Color(0, 0, 0, 120));
        g.drawRect(x + 1, y + 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2);

        int dot = Math.max(2, BLOCK_SIZE / 8);
        g.setColor(darker.darker());
        g.fillRect(x + BLOCK_SIZE / 2 - dot / 2, y + BLOCK_SIZE / 2 - dot / 2, dot, dot);
    }

    private void drawDesignButtons(Graphics2D g) {
        for (int i = 0; i < designButtons.length; i++) {
            Rectangle btn = designButtons[i];
            boolean isSelected = (i == guiDesign);
            
            if (guiDesign == 3) {
                // Windows 95 style buttons
                Color btnColor = new Color(192, 192, 192);
                g.setColor(btnColor);
                g.fillRect(btn.x, btn.y, btn.width, btn.height);
                
                g.setColor(isSelected ? new Color(128, 128, 128) : new Color(255, 255, 255));
                g.drawLine(btn.x, btn.y, btn.x + btn.width, btn.y);
                g.drawLine(btn.x, btn.y, btn.x, btn.y + btn.height);
                
                g.setColor(isSelected ? new Color(255, 255, 255) : new Color(128, 128, 128));
                g.drawLine(btn.x + btn.width, btn.y, btn.x + btn.width, btn.y + btn.height);
                g.drawLine(btn.x, btn.y + btn.height, btn.x + btn.width, btn.y + btn.height);
                
                g.setColor(new Color(0, 0, 0));
                g.setFont(new Font("MS Sans Serif", Font.PLAIN, 10));
                FontMetrics fm = g.getFontMetrics();
                int textX = btn.x + (btn.width - fm.stringWidth(designNames[i])) / 2;
                int textY = btn.y + ((btn.height - fm.getHeight()) / 2) + fm.getAscent();
                g.drawString(designNames[i], textX, textY);
            } else {
                // Colorful buttons for other designs
                Color btnColor;
                if (guiDesign == 0) {
                    btnColor = ColorCycler.getPastelRainbowColor(colorCycleTime, i);
                } else if (guiDesign == 1) {
                    Color[] colors = {new Color(255, 165, 0), new Color(255, 100, 0), new Color(255, 69, 0), new Color(220, 80, 0)};
                    btnColor = colors[i];
                } else {
                    Color[] colors = {new Color(200, 200, 0), new Color(200, 0, 0), new Color(0, 200, 200), new Color(200, 100, 0)};
                    btnColor = colors[i];
                }
                
                g.setColor(btnColor);
                g.fillRect(btn.x, btn.y, btn.width, btn.height);
                
                if (isSelected) {
                    g.setStroke(new BasicStroke(3));
                    g.setColor(new Color(255, 255, 255));
                } else {
                    g.setStroke(new BasicStroke(1));
                    g.setColor(new Color(0, 0, 0));
                }
                g.drawRect(btn.x, btn.y, btn.width, btn.height);
                
                g.setColor(new Color(0, 0, 0));
                g.setFont(new Font("Arial", Font.BOLD, 9));
                FontMetrics fm = g.getFontMetrics();
                int textX = btn.x + (btn.width - fm.stringWidth(designNames[i])) / 2;
                int textY = btn.y + ((btn.height - fm.getHeight()) / 2) + fm.getAscent();
                g.drawString(designNames[i], textX, textY);
            }
        }
    }

    private void drawScore(Graphics2D g) {
        Color scoreColor;
        Font scoreFont;
        
        if (guiDesign == 0) {
            scoreColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 4);
            scoreFont = new Font("Arial", Font.BOLD, 20);
        } else if (guiDesign == 1) {
            scoreColor = new Color(255, 165, 0);
            scoreFont = new Font("Arial", Font.BOLD, 20);
        } else if (guiDesign == 2) {
            scoreColor = new Color(200, 200, 0);
            scoreFont = new Font("Courier New", Font.BOLD, 20);
        } else {
            scoreColor = new Color(0, 0, 0);
            scoreFont = new Font("MS Sans Serif", Font.BOLD, 14);
        }
        
        g.setColor(scoreColor);
        g.setFont(scoreFont);
        g.drawString("Score: " + score, 10, 30);
    }

    private void drawNextPiece(Graphics2D g) {
        int previewX = 10;
        int previewY = 100;
        int previewBlockSize = 20;
        int boxSize = 120;

        Color labelColor, boxColor;
        Font labelFont;
        
        if (guiDesign == 0) {
            labelColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 5);
            boxColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 6);
            labelFont = new Font("Arial", Font.BOLD, 14);
        } else if (guiDesign == 1) {
            labelColor = new Color(255, 165, 0);
            boxColor = new Color(255, 165, 0);
            labelFont = new Font("Arial", Font.BOLD, 14);
        } else if (guiDesign == 2) {
            labelColor = new Color(200, 200, 0);
            boxColor = new Color(200, 200, 0);
            labelFont = new Font("Courier New", Font.BOLD, 14);
        } else {
            labelColor = new Color(0, 0, 0);
            boxColor = new Color(0, 0, 0);
            labelFont = new Font("MS Sans Serif", Font.PLAIN, 11);
        }

        g.setColor(labelColor);
        g.setFont(labelFont);
        g.drawString("Next:", previewX, previewY);
        g.setColor(boxColor);
        
        if (guiDesign == 3) {
            // Windows 95 beveled box
            g.setColor(new Color(192, 192, 192));
            g.fillRect(previewX, previewY + 15, boxSize, boxSize);
            g.setColor(new Color(255, 255, 255));
            g.drawLine(previewX, previewY + 15, previewX + boxSize, previewY + 15);
            g.drawLine(previewX, previewY + 15, previewX, previewY + 15 + boxSize);
            g.setColor(new Color(128, 128, 128));
            g.drawLine(previewX + boxSize, previewY + 15, previewX + boxSize, previewY + 15 + boxSize);
            g.drawLine(previewX, previewY + 15 + boxSize, previewX + boxSize, previewY + 15 + boxSize);
        } else {
            g.drawRect(previewX, previewY + 15, boxSize, boxSize);
        }

        int[][] nextShape = nextTetromino.shape;
        int shapeWidth = nextShape[0].length;
        int shapeHeight = nextShape.length;
        int offsetX = previewX + (boxSize - shapeWidth * previewBlockSize) / 2;
        int offsetY = previewY + 15 + (boxSize - shapeHeight * previewBlockSize) / 2;

        for (int i = 0; i < nextShape.length; i++) {
            for (int j = 0; j < nextShape[0].length; j++) {
                if (nextShape[i][j] == 1) {
                    int x = offsetX + j * previewBlockSize;
                    int y = offsetY + i * previewBlockSize;
                    Color color = getDesignColor(nextTetromino.color);
                    g.setColor(color);
                    g.fillRect(x, y, previewBlockSize - 1, previewBlockSize - 1);
                    g.setColor(color.darker());
                    g.drawRect(x, y, previewBlockSize - 1, previewBlockSize - 1);
                }
            }
        }
    }

    private void drawHeldPiece(Graphics2D g) {
        int previewX = 10;
        int previewY = 350;
        int previewBlockSize = 20;
        int boxSize = 120;

        Color labelColor, boxColor;
        Font labelFont;
        
        if (guiDesign == 0) {
            labelColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 7);
            boxColor = ColorCycler.getPastelRainbowColor(colorCycleTime, 8);
            labelFont = new Font("Arial", Font.BOLD, 14);
        } else if (guiDesign == 1) {
            labelColor = new Color(255, 100, 0);
            boxColor = new Color(255, 100, 0);
            labelFont = new Font("Arial", Font.BOLD, 14);
        } else if (guiDesign == 2) {
            labelColor = new Color(200, 200, 0);
            boxColor = new Color(200, 200, 0);
            labelFont = new Font("Courier New", Font.BOLD, 14);
        } else {
            labelColor = new Color(0, 0, 0);
            boxColor = new Color(0, 0, 0);
            labelFont = new Font("MS Sans Serif", Font.PLAIN, 11);
        }

        g.setColor(labelColor);
        g.setFont(labelFont);
        g.drawString("Hold [C]:", previewX, previewY);
        g.setColor(boxColor);
        
        if (guiDesign == 3) {
            // Windows 95 beveled box
            g.setColor(new Color(192, 192, 192));
            g.fillRect(previewX, previewY + 15, boxSize, boxSize);
            g.setColor(new Color(255, 255, 255));
            g.drawLine(previewX, previewY + 15, previewX + boxSize, previewY + 15);
            g.drawLine(previewX, previewY + 15, previewX, previewY + 15 + boxSize);
            g.setColor(new Color(128, 128, 128));
            g.drawLine(previewX + boxSize, previewY + 15, previewX + boxSize, previewY + 15 + boxSize);
            g.drawLine(previewX, previewY + 15 + boxSize, previewX + boxSize, previewY + 15 + boxSize);
        } else {
            g.drawRect(previewX, previewY + 15, boxSize, boxSize);
        }

        if (heldTetromino != null) {
            int[][] holdShape = heldTetromino.shape;
            int shapeWidth = holdShape[0].length;
            int shapeHeight = holdShape.length;
            int offsetX = previewX + (boxSize - shapeWidth * previewBlockSize) / 2;
            int offsetY = previewY + 15 + (boxSize - shapeHeight * previewBlockSize) / 2;

            for (int i = 0; i < holdShape.length; i++) {
                for (int j = 0; j < holdShape[0].length; j++) {
                    if (holdShape[i][j] == 1) {
                        int x = offsetX + j * previewBlockSize;
                        int y = offsetY + i * previewBlockSize;
                        Color color = getDesignColor(heldTetromino.color);
                        g.setColor(color);
                        g.fillRect(x, y, previewBlockSize - 1, previewBlockSize - 1);
                        g.setColor(color.darker());
                        g.drawRect(x, y, previewBlockSize - 1, previewBlockSize - 1);
                    }
                }
            }
        }
    }

    private void drawGameOver(Graphics2D g) {
        switch (guiDesign) {
            case 0:
                drawGameOverDefault(g);
                break;
            case 1:
                drawGameOverSunset(g);
                break;
            case 2:
                drawGameOverRetro(g);
                break;
            case 3:
                drawGameOverWin95(g);
                break;
        }
    }

    private void drawGameOverDefault(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawDesignButtons(g);
        g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 0));
        g.setFont(new Font("Arial", Font.BOLD, 36));
        g.drawString("GAME OVER", 50, 150);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 1));
        g.drawString("Final Score: " + score, 70, 200);
        g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 2));
        g.drawString("Lines: " + lines + " | Level: " + level, 70, 220);
        g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 3));
        g.drawString("High Scores:", 70, 250);
        for (int i = 0; i < highScores.size(); i++) {
            g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 4 + i));
            g.drawString((i + 1) + ". " + highScores.get(i), 70, 270 + i * 20);
        }
        g.setColor(ColorCycler.getPastelRainbowColor(colorCycleTime, 5));
        g.drawString("Press R to Restart | Press D to Change Design", 30, 270 + highScores.size() * 20 + 20);
        
        // Bild für 2 Sekunden anzeigen
        if (gameOverImageTime > 0 && gameOverImage != null) {
            long elapsedTime = System.currentTimeMillis() - gameOverImageTime;
            if (elapsedTime < IMAGE_DISPLAY_TIME) {
                try {
                    int imgWidth = gameOverImage.getWidth();
                    int imgHeight = gameOverImage.getHeight();
                    
                    if (imgWidth > 0 && imgHeight > 0) {
                        int maxWidth = WIDTH - 100;
                        int maxHeight = HEIGHT - 400;
                        double scale = Math.min((double) maxWidth / imgWidth, 
                                               (double) maxHeight / imgHeight);
                        int displayWidth = (int) (imgWidth * scale);
                        int displayHeight = (int) (imgHeight * scale);
                        int x = (WIDTH - displayWidth) / 2;
                        int y = (HEIGHT - displayHeight) / 2;
                        
                        g.drawImage(gameOverImage, x, y, displayWidth, displayHeight, null);
                        System.out.println("Bild wird angezeigt: " + displayWidth + "x" + displayHeight + " (vergangen: " + elapsedTime + "ms)");
                    }
                } catch (Exception e) {
                    System.err.println("Fehler beim Zeichnen des Bildes: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void drawGameOverSunset(Graphics2D g) {
        g.setColor(new Color(40, 20, 10, 220));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawDesignButtons(g);
        
        // Sunset glow effect
        g.setColor(new Color(255, 100, 0, 100));
        g.fillRect(40, 130, 300, 80);
        
        g.setColor(new Color(255, 165, 0));
        g.setFont(new Font("Arial", Font.BOLD, 48));
        g.drawString("GAME OVER", 45, 180);
        
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(new Color(255, 140, 0));
        g.drawString("Final Score: " + score, 70, 220);
        g.setColor(new Color(255, 100, 0));
        g.drawString("Lines: " + lines + " | Level: " + level, 70, 240);
        
        g.setColor(new Color(255, 165, 0));
        g.drawString("Top Scores:", 70, 270);
        for (int i = 0; i < highScores.size(); i++) {
            if (i % 2 == 0) {
                g.setColor(new Color(255, 140, 0));
            } else {
                g.setColor(new Color(220, 80, 0));
            }
            g.drawString((i + 1) + ". " + highScores.get(i), 70, 290 + i * 20);
        }
        
        g.setColor(new Color(255, 180, 50));
        g.drawString(">> Press R to Restart | Press D to Change Design <<", 20, 280 + highScores.size() * 20 + 20);
    }

    private void drawGameOverRetro(Graphics2D g) {
        g.setColor(new Color(50, 50, 50, 200));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawDesignButtons(g);
        
        // Draw retro border
        g.setColor(new Color(200, 200, 0));
        g.setStroke(new BasicStroke(4));
        g.drawRect(30, 120, 350, 400);
        
        g.setFont(new Font("Courier New", Font.BOLD, 32));
        g.setColor(new Color(200, 200, 0));
        g.drawString("GAME OVER", 80, 180);
        
        g.setFont(new Font("Courier New", Font.PLAIN, 14));
        g.setColor(new Color(255, 255, 255));
        g.drawString("Final Score: " + score, 70, 220);
        g.drawString("Lines: " + lines, 70, 240);
        g.drawString("Level: " + level, 70, 260);
        
        g.setColor(new Color(200, 200, 0));
        g.drawString("High Scores:", 70, 290);
        g.setColor(new Color(255, 255, 255));
        for (int i = 0; i < highScores.size(); i++) {
            g.drawString((i + 1) + ". " + highScores.get(i), 70, 310 + i * 18);
        }
        
        g.setColor(new Color(200, 200, 0));
        g.drawString("[R] RESTART  [D] DESIGN", 60, 280 + highScores.size() * 18 + 40);
    }

    private void drawGameOverWin95(Graphics2D g) {
        // Windows 95 style game over screen
        g.setColor(new Color(192, 192, 192));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        // Draw window frame
        int winX = 75;
        int winY = 50;
        int winW = 550;
        int winH = 620;
        
        // Window background
        g.setColor(new Color(192, 192, 192));
        g.fillRect(winX, winY, winW, winH);
        
        // Outer border (white/gray 3D effect)
        g.setColor(new Color(255, 255, 255));
        g.drawLine(winX, winY, winX + winW, winY);
        g.drawLine(winX, winY, winX, winY + winH);
        
        g.setColor(new Color(128, 128, 128));
        g.drawLine(winX + winW, winY, winX + winW, winY + winH);
        g.drawLine(winX, winY + winH, winX + winW, winY + winH);
        
        // Title bar
        g.setColor(new Color(0, 0, 170));
        g.fillRect(winX, winY, winW, 25);
        g.setColor(new Color(255, 255, 255));
        g.setFont(new Font("MS Sans Serif", Font.BOLD, 14));
        g.drawString("Tetris - Game Over", winX + 10, winY + 18);
        
        // Content
        g.setColor(new Color(0, 0, 0));
        g.setFont(new Font("MS Sans Serif", Font.BOLD, 24));
        g.drawString("GAME OVER", winX + 150, winY + 70);
        
        g.setFont(new Font("MS Sans Serif", Font.PLAIN, 12));
        g.drawString("Final Score: " + score, winX + 50, winY + 120);
        g.drawString("Lines Cleared: " + lines, winX + 50, winY + 145);
        g.drawString("Level Reached: " + level, winX + 50, winY + 170);
        
        g.setColor(new Color(0, 0, 170));
        g.drawString("High Scores:", winX + 50, winY + 210);
        
        g.setColor(new Color(0, 0, 0));
        for (int i = 0; i < Math.min(5, highScores.size()); i++) {
            g.drawString((i + 1) + ". " + highScores.get(i), winX + 70, winY + 235 + i * 18);
        }
        
        g.setFont(new Font("MS Sans Serif", Font.PLAIN, 11));
        g.drawString("Press [R] to Restart", winX + 50, winY + winH - 35);
        
        // Draw buttons below the window
        drawDesignButtons(g);
    }

    private Color getColorForValue(int value) {
        if (value != 0) return Color.WHITE;
        return Color.BLACK;
    }
}

class Tetromino {
    int[][] shape;
    int x;
    int y;
    int color;

    private static final int[][][] SHAPES = {
        {{1, 1, 1, 1}},
        {{1, 1}, {1, 1}},
        {{0, 1, 1}, {1, 1, 0}},
        {{1, 1, 0}, {0, 1, 1}},
        {{1, 0, 0}, {1, 1, 1}},
        {{0, 0, 1}, {1, 1, 1}},
        {{0, 1, 0}, {1, 1, 1}}
    };

    public Tetromino() {
        int randomShape = (int) (Math.random() * SHAPES.length);
        shape = SHAPES[randomShape];
        color = randomShape + 1;
        x = 3;
        y = 0;
    }
}

// Color cycling utility method for pastel rainbow effect
class ColorCycler {
    public static Color getPastelRainbowColor(long timeMs, int offset) {
        // Cycle through pastel rainbow colors
        double cycle = (timeMs + offset * 30) % 4000 / 4000.0; // 4 second cycle
        double hue = cycle;
        
        // Pastel colors - high saturation for vibrancy but light tints
        float saturation = 0.5f;
        float brightness = 0.8f;
        
        Color c = Color.getHSBColor((float) hue, saturation, brightness);
        return c;
    }
}

