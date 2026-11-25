import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

public class StudentPerformanceApp {

    // ================= THEME COLORS & FONTS =================
    static final Color PRIMARY = new Color(37, 99, 235);
    static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    static final Color EDIT_COLOR = new Color(14, 165, 233);
    static final Color DANGER = new Color(220, 38, 38);
    static final Color DANGER_HOVER = new Color(185, 28, 28);
    static final Color SLATE = new Color(71, 85, 105);
    static final Color SLATE_HOVER = new Color(51, 65, 85);
    static final Color WARNING = new Color(217, 119, 6);
    static final Color SUCCESS = new Color(22, 163, 74);

    static final Color CARD_BG = new Color(255, 255, 255, 240);
    static final Color CARD_BORDER = new Color(255, 255, 255, 180);
    static final Color TEXT_DARK = new Color(15, 23, 42);
    static final Color TEXT_MUTED = new Color(100, 116, 139);
    static final Color ROW_ALT = new Color(248, 250, 252);

    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);

    // ================= BACKGROUND MANAGER =================
    static final List<BufferedImage> BG_IMAGES = new ArrayList<>();
    static final List<String> BG_NAMES = new ArrayList<>();
    static int currentBgIndex = 0;

    static {
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().matches("^bg.*\\.(jpg|jpeg|png)$"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        BG_IMAGES.add(img);
                        BG_NAMES.add(f.getName());
                    }
                } catch (IOException ignored) {}
            }
        }
    }

    // ================= RISK CALCULATION HELPERS =================
    static String getMarksRisk(double marks) {
        if (marks < 40) return "HIGH (Fail Risk)";
        if (marks < 60) return "MEDIUM";
        return "LOW (Safe)";
    }

    static String getAttendanceRisk(double attendance) {
        if (attendance < 75) return "HIGH (Detain Risk)";
        if (attendance < 85) return "MEDIUM";
        return "LOW (Safe)";
    }

    static String getCombinedRisk(double marks, double attendance) {
        if (marks < 40 && attendance < 75) return "HIGH (Detain & Fail)";
        if (marks < 40) return "HIGH (Fail Risk)";
        if (attendance < 75) return "HIGH (Detain Risk)";
        if (marks < 60 || attendance < 85) return "MEDIUM RISK";
        return "LOW RISK (Safe)";
    }

    static boolean validMarks(double marks) {
        return marks >= 0 && marks <= 100;
    }

    static Color getRiskColor(String riskText) {
        if (riskText.startsWith("HIGH")) return DANGER;
        if (riskText.startsWith("MEDIUM")) return WARNING;
        if (riskText.startsWith("LOW")) return SUCCESS;
        return TEXT_DARK;
    }

    // ================= DATABASE HELPER =================
    static class DB {
        private static final Properties props = new Properties();

        static {
            try (FileInputStream fis = new FileInputStream(".env")) {
                props.load(fis);
            } catch (IOException e) {
                System.out.println("Could not load .env file, falling back to system environment variables.");
            }
        }

        private static String get(String key, String fallback) {
            if (props.containsKey(key)) return props.getProperty(key).trim();
            String envVal = System.getenv(key);
            return envVal != null ? envVal.trim() : fallback;
        }

        private static final String URL = get("DB_URL", "jdbc:mysql://localhost:3306/student_analysis?sslMode=DISABLED");
        private static final String USER = get("DB_USER", "avnadmin");
        private static final String PASS = get("DB_PASS", "");

        public static void initializeSchema() {
            try (Connection con = DriverManager.getConnection(URL, USER, PASS);
                 Statement st = con.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "full_name VARCHAR(150) NOT NULL, " +
                        "password VARCHAR(255) NOT NULL, " +
                        "role VARCHAR(20) NOT NULL, " +
                        "class_name VARCHAR(50) NOT NULL DEFAULT '', " +
                        "department VARCHAR(100) NOT NULL DEFAULT '', " +
                        "is_verified BOOLEAN NOT NULL DEFAULT FALSE)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS performance (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "student_id INT NOT NULL, " +
                        "subject VARCHAR(100) NOT NULL, " +
                        "mid_term_marks DECIMAL(5,2) NOT NULL DEFAULT 0, " +
                        "ce1_marks DECIMAL(5,2) NOT NULL DEFAULT 0, " +
                        "ce2_marks DECIMAL(5,2) NOT NULL DEFAULT 0, " +
                        "final_marks DECIMAL(5,2) NOT NULL DEFAULT 0, " +
                        "attendance DECIMAL(5,2) NOT NULL DEFAULT 0)");

                ensureColumn(con, "users", "class_name", "VARCHAR(50) NOT NULL DEFAULT ''");
                ensureColumn(con, "users", "department", "VARCHAR(100) NOT NULL DEFAULT ''");
                ensureColumn(con, "users", "is_verified", "BOOLEAN NOT NULL DEFAULT FALSE");
                ensureColumn(con, "performance", "mid_term_marks", "DECIMAL(5,2) NOT NULL DEFAULT 0");
                ensureColumn(con, "performance", "ce1_marks", "DECIMAL(5,2) NOT NULL DEFAULT 0");
                ensureColumn(con, "performance", "ce2_marks", "DECIMAL(5,2) NOT NULL DEFAULT 0");
                ensureColumn(con, "performance", "final_marks", "DECIMAL(5,2) NOT NULL DEFAULT 0");
                migrateLegacyMarks(con);
                ensureColumn(con, "users", "full_name", "VARCHAR(150) NOT NULL DEFAULT ''");
                migrateLegacyNames(con);
                seedTeacherAccount(con);
            } catch (SQLException ex) {
                System.err.println("Database initialization failed: " + ex.getMessage());
            }
        }

        private static void ensureColumn(Connection con, String table, String column, String definition) throws SQLException {
            String sql = "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name=? AND column_name=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, table);
                ps.setString(2, column);
                ResultSet rs = ps.executeQuery();
                rs.next();
                if (rs.getInt(1) == 0) {
                    try (Statement st = con.createStatement()) {
                        st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    }
                }
            }
        }

        private static void migrateLegacyMarks(Connection con) throws SQLException {
            String sql = "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name='performance' AND column_name='marks'";
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    st.executeUpdate("ALTER TABLE performance MODIFY COLUMN marks DECIMAL(5,2) NOT NULL DEFAULT 0");
                    st.executeUpdate("UPDATE performance SET final_marks=marks " +
                            "WHERE mid_term_marks=0 AND ce1_marks=0 AND ce2_marks=0 AND final_marks=0");
                }
            }
        }

        private static void migrateLegacyNames(Connection con) throws SQLException {
            if (columnExists(con, "users", "username")) {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("UPDATE users SET full_name=username WHERE full_name='' OR full_name IS NULL");
                    st.executeUpdate("ALTER TABLE users MODIFY COLUMN username VARCHAR(100) NULL");
                }
            }
            if (columnExists(con, "users", "email")) {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("UPDATE users SET full_name=email WHERE full_name='' OR full_name IS NULL");
                    st.executeUpdate("ALTER TABLE users MODIFY COLUMN email VARCHAR(150) NULL");
                }
            }
        }

        private static boolean columnExists(Connection con, String table, String column) throws SQLException {
            String sql = "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name=? AND column_name=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, table);
                ps.setString(2, column);
                ResultSet rs = ps.executeQuery();
                rs.next();
                return rs.getInt(1) > 0;
            }
        }

        private static void seedTeacherAccount(Connection con) throws SQLException {
            String name = get("TEACHER_NAME", "Teacher");
            String password = get("TEACHER_PASSWORD", "Teacher@123");
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT id FROM users WHERE LOWER(TRIM(full_name))=? AND role='TEACHER'")) {
                check.setString(1, name.trim().toLowerCase());
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE users SET password=?, is_verified=TRUE WHERE id=?")) {
                        update.setString(1, password);
                        update.setInt(2, rs.getInt("id"));
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO users (full_name, password, role, class_name, department, is_verified) " +
                            "VALUES (?, ?, 'TEACHER', '', 'Administration', TRUE)")) {
                        insert.setString(1, name.trim());
                        insert.setString(2, password);
                        insert.executeUpdate();
                    }
                }
            }
        }

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASS);
        }
    }

    // ================= CUSTOM UI COMPONENTS =================
    static class BackgroundPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        public void nextBackground(Component parent) {
            if (BG_IMAGES.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "No bg*.jpg or bg*.png images found in your project folder.");
                return;
            }
            currentBgIndex = (currentBgIndex + 1) % BG_IMAGES.size();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (!BG_IMAGES.isEmpty()) {
                BufferedImage img = BG_IMAGES.get(currentBgIndex);
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                g2.setColor(new Color(15, 23, 42, 160));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                GradientPaint gp = new GradientPaint(0, 0, new Color(15, 23, 42),
                        getWidth(), getHeight(), new Color(30, 58, 138));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    static class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final int radius;
        private final Color bgColor;

        public RoundedPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static JButton createStyledButton(String text, Color bg, Color hoverBg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 16, 9, 16));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBg); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    static void styleTextField(JTextField field) {
        field.setFont(FONT_REGULAR);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(7, 10, 7, 10)
        ));
    }

    static void styleTable(JTable table) {
        table.setFont(FONT_REGULAR);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(TEXT_DARK);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(30, 41, 59));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 38));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer zebraRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                setHorizontalAlignment(JLabel.CENTER);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                }
                c.setForeground(TEXT_DARK);
                c.setFont(FONT_REGULAR);
                return c;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(zebraRenderer);
        }
    }

    // ================= LOGIN / REGISTER FRAME =================
    static class LoginFrame extends JFrame {
        private static final long serialVersionUID = 1L;

        private final JTextField nameField = new JTextField(18);
        private final JPasswordField passField = new JPasswordField(18);
        private final JTextField classField = new JTextField(18);
        private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"STUDENT", "TEACHER"});
        private final JLabel classLabel = new JLabel("Class");
        private final JCheckBox rememberLoginBox = new JCheckBox("Remember this login on this laptop");
        private final Preferences savedLogins =
                Preferences.userNodeForPackage(StudentPerformanceApp.class).node("saved-logins");

        public LoginFrame() {
            setTitle("Student Performance Analysis System");
            setSize(540, 690);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout());
            setContentPane(bg);

            // Top bar with Switch BG always visible
            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
            topBar.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            topBar.add(bgBtn);
            bg.add(topBar, BorderLayout.NORTH);

            JPanel centerWrap = new JPanel(new GridBagLayout());
            centerWrap.setOpaque(false);

            RoundedPanel card = new RoundedPanel(22, CARD_BG);
            card.setLayout(new GridBagLayout());
            card.setBorder(new EmptyBorder(28, 36, 28, 36));

            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(6, 4, 6, 4);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0; g.gridwidth = 2;

            JLabel title = new JLabel("Performance Portal", JLabel.CENTER);
            title.setFont(FONT_TITLE);
            title.setForeground(TEXT_DARK);
            g.gridy = 0;
            card.add(title, g);

            JLabel subtitle = new JLabel("Sign in or register to access your dashboard", JLabel.CENTER);
            subtitle.setFont(FONT_SUBTITLE);
            subtitle.setForeground(TEXT_MUTED);
            g.gridy = 1;
            g.insets = new Insets(0, 4, 16, 4);
            card.add(subtitle, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 2;
            JLabel uLbl = new JLabel("Name");
            uLbl.setFont(FONT_BOLD);
            card.add(uLbl, g);

            styleTextField(nameField);
            g.gridy = 3;
            g.insets = new Insets(0, 4, 10, 4);
            card.add(nameField, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 4;
            JLabel pLbl = new JLabel("Password");
            pLbl.setFont(FONT_BOLD);
            card.add(pLbl, g);

            styleTextField(passField);
            g.gridy = 5;
            g.insets = new Insets(0, 4, 10, 4);
            card.add(passField, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 6;
            card.add(classLabel, g);
            styleTextField(classField);
            g.gridy = 7;
            g.insets = new Insets(0, 4, 10, 4);
            card.add(classField, g);

            rememberLoginBox.setOpaque(false);
            rememberLoginBox.setFont(FONT_REGULAR);
            g.gridy = 8;
            g.insets = new Insets(2, 4, 8, 4);
            card.add(rememberLoginBox, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 9;
            JLabel rLbl = new JLabel("Role");
            rLbl.setFont(FONT_BOLD);
            card.add(rLbl, g);

            roleBox.setFont(FONT_REGULAR);
            roleBox.setBackground(Color.WHITE);
            g.gridy = 10;
            g.insets = new Insets(0, 4, 18, 4);
            card.add(roleBox, g);

            roleBox.addActionListener(e -> {
                updateRoleFields();
                loadSavedLogin();
            });

            JPanel btnPanel = new JPanel(new GridLayout(1, 2, 12, 0));
            btnPanel.setOpaque(false);
            JButton loginBtn = createStyledButton("Login", PRIMARY, PRIMARY_HOVER);
            JButton regBtn = createStyledButton("Register", SLATE, SLATE_HOVER);
            btnPanel.add(loginBtn);
            btnPanel.add(regBtn);

            g.gridy = 11;
            g.insets = new Insets(4, 4, 0, 4);
            card.add(btnPanel, g);

            centerWrap.add(card);
            bg.add(centerWrap, BorderLayout.CENTER);

            loginBtn.addActionListener(e -> handleLogin());
            regBtn.addActionListener(e -> handleRegister());
            updateRoleFields();
            loadSavedLogin();
        }

        private void updateRoleFields() {
            boolean student = "STUDENT".equals(roleBox.getSelectedItem());
            classLabel.setVisible(student);
            classField.setVisible(student);
            setSize(540, student ? 690 : 540);
            revalidate();
            repaint();
        }

        private String preferencePrefix() {
            return "TEACHER".equals(roleBox.getSelectedItem()) ? "teacher." : "student.";
        }

        private void loadSavedLogin() {
            String prefix = preferencePrefix();
            try {
                String savedName = savedLogins.get(prefix + "name", null);
                boolean hasSavedLogin = savedName != null;
                nameField.setText(hasSavedLogin ? savedName : "");
                passField.setText(hasSavedLogin ? savedLogins.get(prefix + "password", "") : "");
                classField.setText(hasSavedLogin ? savedLogins.get(prefix + "class", "") : "");
                rememberLoginBox.setSelected(hasSavedLogin);
            } catch (SecurityException ex) {
                JOptionPane.showMessageDialog(this, "Could not read saved login details: " + ex.getMessage(),
                        "Saved Login Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void saveLogin() {
            String prefix = preferencePrefix();
            try {
                if (rememberLoginBox.isSelected()) {
                    savedLogins.put(prefix + "name", nameField.getText().trim());
                    savedLogins.put(prefix + "password", new String(passField.getPassword()));
                    savedLogins.put(prefix + "class", classField.getText().trim());
                } else {
                    savedLogins.remove(prefix + "name");
                    savedLogins.remove(prefix + "password");
                    savedLogins.remove(prefix + "class");
                }
                savedLogins.flush();
            } catch (SecurityException | BackingStoreException ex) {
                JOptionPane.showMessageDialog(this, "Could not save login details: " + ex.getMessage(),
                        "Saved Login Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleLogin() {
            String name = nameField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String className = classField.getText().trim();
            String role = (String) roleBox.getSelectedItem();

            String sql = "SELECT id, is_verified FROM users " +
                    "WHERE LOWER(TRIM(full_name))=? AND password=? AND role=?" +
                    ("STUDENT".equals(role) ? " AND LOWER(TRIM(class_name))=LOWER(TRIM(?))" : "");
            if ("STUDENT".equals(role) && className.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter your registered class to log in.",
                        "Class Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, name.toLowerCase());
                ps.setString(2, pass);
                ps.setString(3, role);
                if ("STUDENT".equals(role)) {
                    ps.setString(4, className);
                }

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    if ("STUDENT".equals(role) && !rs.getBoolean("is_verified")) {
                        JOptionPane.showMessageDialog(this,
                                "This account is awaiting verification. Ask a teacher or administrator to verify it.",
                                "Verification Required", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    int userId = rs.getInt("id");
                    saveLogin();
                    dispose();
                    if ("TEACHER".equals(role)) {
                        new TeacherDashboard(userId, name).setVisible(true);
                    } else {
                        new StudentDashboard(userId, name).setVisible(true);
                    }
                } else {
                    String defaultTeacherName = DB.get("TEACHER_NAME", "Teacher");
                    String defaultTeacherPassword = DB.get("TEACHER_PASSWORD", "Teacher@123");
                    if ("TEACHER".equals(role)
                            && name.equalsIgnoreCase(defaultTeacherName.trim())
                            && pass.equals(defaultTeacherPassword)) {
                        saveLogin();
                        dispose();
                        new TeacherDashboard(0, defaultTeacherName.trim()).setVisible(true);
                    } else {
                        String message = "STUDENT".equals(role)
                                ? "Name, password, or class is incorrect."
                                : "Invalid name or password.";
                        JOptionPane.showMessageDialog(this, message, "Login Failed", JOptionPane.WARNING_MESSAGE);
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleRegister() {
            String name = nameField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String className = classField.getText().trim();
            String role = (String) roleBox.getSelectedItem();

            if (name.isEmpty() || pass.isEmpty() ||
                    ("STUDENT".equals(role) && className.isEmpty())) {
                JOptionPane.showMessageDialog(this,
                        "STUDENT".equals(role)
                                ? "Name, password and class are required."
                                : "Name and password are required.");
                return;
            }
            if (pass.length() < 8) {
                JOptionPane.showMessageDialog(this, "Password must contain at least 8 characters.");
                return;
            }

            String sql = "INSERT INTO users (full_name, password, role, class_name, department, is_verified) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, pass);
                ps.setString(3, role);
                ps.setString(4, "TEACHER".equals(role) ? "" : className);
                ps.setString(5, "TEACHER".equals(role) ? className : "");
                ps.setBoolean(6, "TEACHER".equals(role));
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                String idMsg = keys.next() ? " (Assigned ID: " + keys.getInt(1) + ")" : "";
                String result = "TEACHER".equals(role)
                        ? "Teacher registered successfully!" + idMsg + "\nYou can now log in."
                        : "Student registered successfully!" + idMsg + "\nThe account is awaiting teacher verification.";
                JOptionPane.showMessageDialog(this, result);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================= TEACHER DASHBOARD =================
    static class TeacherDashboard extends JFrame {
        private static final long serialVersionUID = 1L;

        private final JComboBox<String> studentBox = new JComboBox<>();
        private transient final Map<String, Integer> studentMap = new HashMap<>();
        private final JTextField subjectField = new JTextField(12);
        private final JTextField midTermField = new JTextField(5);
        private final JTextField ce1Field = new JTextField(5);
        private final JTextField ce2Field = new JTextField(5);
        private final JTextField finalField = new JTextField(5);
        private final JTextField attendanceField = new JTextField(6);
        private final JTextField studentSearchField = new JTextField(14);
        private final JComboBox<String> classFilterBox = new JComboBox<>();
        private final DefaultTableModel tableModel = new DefaultTableModel(
                new String[]{"Record ID", "Student ID", "Student Name", "Class", "Subject",
                        "Mid Term", "CE1", "CE2", "Final", "Attendance (%)", "Edit", "Delete"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 10 || column == 11;
            }
        };

        public TeacherDashboard(int teacherId, String teacherName) {
            setTitle("Teacher Dashboard - " + teacherName);
            setSize(1200, 650);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout(15, 15));
            bg.setBorder(new EmptyBorder(18, 22, 22, 22));
            setContentPane(bg);

            // Top Navbar
            RoundedPanel nav = new RoundedPanel(16, CARD_BG);
            nav.setLayout(new BorderLayout());
            nav.setBorder(new EmptyBorder(12, 20, 12, 20));

            JLabel headerLbl = new JLabel("Teacher Dashboard  |  " + teacherName + " (ID: " + teacherId + ")");
            headerLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
            headerLbl.setForeground(TEXT_DARK);

            JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            navBtns.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            navBtns.add(bgBtn);

            JButton logoutBtn = createStyledButton("Logout", DANGER, DANGER_HOVER);
            logoutBtn.addActionListener(e -> {
                dispose();
                new LoginFrame().setVisible(true);
            });
            navBtns.add(logoutBtn);

            JButton verifyBtn = createStyledButton("Verify Students", WARNING, new Color(180, 83, 9));
            verifyBtn.addActionListener(e -> openVerificationDialog());
            navBtns.add(verifyBtn);

            nav.add(headerLbl, BorderLayout.WEST);
            nav.add(navBtns, BorderLayout.EAST);


            JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
            centerPanel.setOpaque(false);

  
            RoundedPanel formCard = new RoundedPanel(16, CARD_BG);
            formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
            formCard.setBorder(new EmptyBorder(8, 12, 8, 12));
            JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            searchRow.setOpaque(false);
            JPanel marksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            marksRow.setOpaque(false);

            studentBox.setFont(FONT_REGULAR);
            studentBox.setBackground(Color.WHITE);
            styleTextField(subjectField);
            styleTextField(midTermField);
            styleTextField(ce1Field);
            styleTextField(ce2Field);
            styleTextField(finalField);
            styleTextField(attendanceField);
            styleTextField(studentSearchField);
            classFilterBox.setFont(FONT_REGULAR);
            classFilterBox.setBackground(Color.WHITE);

            JButton addBtn = createStyledButton("+ Save Record", PRIMARY, PRIMARY_HOVER);
            addBtn.addActionListener(e -> addRecord());
            JButton searchBtn = createStyledButton("Search", EDIT_COLOR, PRIMARY_HOVER);
            searchBtn.addActionListener(e -> loadAllPerformance());

            studentBox.setPreferredSize(new Dimension(220, 32));
            classFilterBox.setPreferredSize(new Dimension(140, 32));
            studentSearchField.setPreferredSize(new Dimension(170, 32));
            subjectField.setPreferredSize(new Dimension(140, 32));
            midTermField.setPreferredSize(new Dimension(70, 32));
            ce1Field.setPreferredSize(new Dimension(70, 32));
            ce2Field.setPreferredSize(new Dimension(70, 32));
            finalField.setPreferredSize(new Dimension(70, 32));
            attendanceField.setPreferredSize(new Dimension(75, 32));

            searchRow.add(new JLabel("Student:"));
            searchRow.add(studentBox);
            searchRow.add(new JLabel("Class:"));
            searchRow.add(classFilterBox);
            searchRow.add(new JLabel("Search:"));
            searchRow.add(studentSearchField);
            searchRow.add(searchBtn);

            marksRow.add(new JLabel("Subject:"));
            marksRow.add(subjectField);
            marksRow.add(new JLabel("Mid Term:"));
            marksRow.add(midTermField);
            marksRow.add(new JLabel("CE1:"));
            marksRow.add(ce1Field);
            marksRow.add(new JLabel("CE2:"));
            marksRow.add(ce2Field);
            marksRow.add(new JLabel("Final:"));
            marksRow.add(finalField);
            marksRow.add(new JLabel("Attendance (%):"));
            marksRow.add(attendanceField);
            marksRow.add(addBtn);

            formCard.add(searchRow);
            formCard.add(marksRow);


            RoundedPanel tableCard = new RoundedPanel(16, CARD_BG);
            tableCard.setLayout(new BorderLayout());
            tableCard.setBorder(new EmptyBorder(12, 12, 12, 12));

            JTable table = new JTable(tableModel);
            styleTable(table);

            table.getColumn("Edit").setCellRenderer(new TableButtonRenderer("Edit", EDIT_COLOR));
            table.getColumn("Edit").setCellEditor(new TableButtonEditor(table, "Edit", EDIT_COLOR, row -> editRecord(table, row)));
            table.getColumn("Edit").setPreferredWidth(80);

            table.getColumn("Delete").setCellRenderer(new TableButtonRenderer("Delete", DANGER));
            table.getColumn("Delete").setCellEditor(new TableButtonEditor(table, "Delete", DANGER, row -> {
                int recordId = (int) table.getValueAt(row, 0);
                deleteRecord(recordId);
            }));
            table.getColumn("Delete").setPreferredWidth(85);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            tableCard.add(scrollPane, BorderLayout.CENTER);

            centerPanel.add(formCard, BorderLayout.NORTH);
            centerPanel.add(tableCard, BorderLayout.CENTER);

            bg.add(nav, BorderLayout.NORTH);
            bg.add(centerPanel, BorderLayout.CENTER);

            loadStudents();
            classFilterBox.addActionListener(e -> loadAllPerformance());
            loadAllPerformance();
        }

        private void loadStudents() {
            studentBox.removeAllItems();
            studentMap.clear();
            classFilterBox.removeAllItems();
            classFilterBox.addItem("All Classes");
            java.util.Set<String> classes = new java.util.LinkedHashSet<>();

            String sql = "SELECT id, full_name, class_name FROM users WHERE role='STUDENT' ORDER BY id ASC";
            try (Connection con = DB.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String fullName = rs.getString("full_name");
                    String name = rs.getString("full_name");
                    String className = rs.getString("class_name");
                    String label = "ID " + id + " - " + name + " (" + (className.isEmpty() ? "No class" : className) + ")";
                    studentMap.put(label, id);
                    studentBox.addItem(label);
                    if (className != null && !className.trim().isEmpty()) {
                        classes.add(className.trim());
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading students: " + ex.getMessage());
            }
            for (String className : classes) classFilterBox.addItem(className);
        }

        private void loadAllPerformance() {
            tableModel.setRowCount(0);
            String search = studentSearchField.getText().trim();
            String selectedClass = (String) classFilterBox.getSelectedItem();
            String sql = "SELECT p.id AS rec_id, u.id AS stu_id, u.full_name, u.class_name, " +
                         "p.subject, p.mid_term_marks, p.ce1_marks, p.ce2_marks, p.final_marks, p.attendance " +
                         "FROM performance p JOIN users u ON p.student_id = u.id WHERE 1=1";
            java.util.List<Object> params = new java.util.ArrayList<>();
            if (selectedClass != null && !"All Classes".equals(selectedClass)) {
                sql += " AND u.class_name=?";
                params.add(selectedClass);
            }
            if (!search.isEmpty()) {
                sql += " AND (u.full_name LIKE ? OR u.class_name LIKE ?)";
                String pattern = "%" + search + "%";
                params.add(pattern); params.add(pattern);
            }
            sql += " ORDER BY p.id DESC";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fullName = rs.getString("full_name");
                        String name = rs.getString("full_name");
                        tableModel.addRow(new Object[]{
                                rs.getInt("rec_id"),
                                rs.getInt("stu_id"),
                                name,
                                rs.getString("class_name"),
                                rs.getString("subject"),
                                rs.getDouble("mid_term_marks"),
                                rs.getDouble("ce1_marks"),
                                rs.getDouble("ce2_marks"),
                                rs.getDouble("final_marks"),
                                rs.getDouble("attendance"),
                                "Edit",
                                "Delete"
                        });
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading records: " + ex.getMessage());
            }
        }

        private void addRecord() {
            String selected = (String) studentBox.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "No students registered yet.");
                return;
            }
            try {
                int studentId = studentMap.get(selected);
                String subject = subjectField.getText().trim();
                double midTerm = Double.parseDouble(midTermField.getText().trim());
                double ce1 = Double.parseDouble(ce1Field.getText().trim());
                double ce2 = Double.parseDouble(ce2Field.getText().trim());
                double finalMarks = Double.parseDouble(finalField.getText().trim());
                double attendance = Double.parseDouble(attendanceField.getText().trim());

                if (subject.isEmpty() || !validMarks(midTerm) || !validMarks(ce1) ||
                        !validMarks(ce2) || !validMarks(finalMarks) ||
                        attendance < 0 || attendance > 100) {
                    JOptionPane.showMessageDialog(this, "Enter a subject name and valid marks/attendance values (0 - 100).");
                    return;
                }

                String sql = "INSERT INTO performance " +
                        "(student_id, subject, mid_term_marks, ce1_marks, ce2_marks, final_marks, attendance) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (Connection con = DB.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, studentId);
                    ps.setString(2, subject);
                    ps.setDouble(3, midTerm);
                    ps.setDouble(4, ce1);
                    ps.setDouble(5, ce2);
                    ps.setDouble(6, finalMarks);
                    ps.setDouble(7, attendance);
                    ps.executeUpdate();
                }

                subjectField.setText("");
                midTermField.setText("");
                ce1Field.setText("");
                ce2Field.setText("");
                finalField.setText("");
                attendanceField.setText("");
                loadAllPerformance();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter valid numbers for marks and attendance.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            }
        }

        private void editRecord(JTable table, int row) {
            int recordId = (int) table.getValueAt(row, 0);
            String studentName = table.getValueAt(row, 2).toString();
            JTextField subF = new JTextField(table.getValueAt(row, 4).toString(), 15);
            JTextField midF = new JTextField(table.getValueAt(row, 5).toString(), 10);
            JTextField ce1F = new JTextField(table.getValueAt(row, 6).toString(), 10);
            JTextField ce2F = new JTextField(table.getValueAt(row, 7).toString(), 10);
            JTextField finalF = new JTextField(table.getValueAt(row, 8).toString(), 10);
            JTextField attF = new JTextField(table.getValueAt(row, 9).toString(), 10);

            JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
            panel.add(new JLabel("Student:"));
            panel.add(new JLabel(studentName + " (Record #" + recordId + ")"));
            panel.add(new JLabel("Subject:"));
            panel.add(subF);
            panel.add(new JLabel("Mid Term (/100):"));
            panel.add(midF);
            panel.add(new JLabel("CE1 (/100):"));
            panel.add(ce1F);
            panel.add(new JLabel("CE2 (/100):"));
            panel.add(ce2F);
            panel.add(new JLabel("Final (/100):"));
            panel.add(finalF);
            panel.add(new JLabel("Attendance (%):"));
            panel.add(attF);

            int result = JOptionPane.showConfirmDialog(this, panel, "Update Record",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;

            try {
                String subject = subF.getText().trim();
                double midTerm = Double.parseDouble(midF.getText().trim());
                double ce1 = Double.parseDouble(ce1F.getText().trim());
                double ce2 = Double.parseDouble(ce2F.getText().trim());
                double finalMarks = Double.parseDouble(finalF.getText().trim());
                double attendance = Double.parseDouble(attF.getText().trim());

                if (subject.isEmpty() || !validMarks(midTerm) || !validMarks(ce1) ||
                        !validMarks(ce2) || !validMarks(finalMarks) ||
                        attendance < 0 || attendance > 100) {
                    JOptionPane.showMessageDialog(this, "Enter a valid subject and values between 0 and 100.");
                    return;
                }

                String sql = "UPDATE performance SET subject=?, mid_term_marks=?, ce1_marks=?, " +
                        "ce2_marks=?, final_marks=?, attendance=? WHERE id=?";
                try (Connection con = DB.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, subject);
                    ps.setDouble(2, midTerm);
                    ps.setDouble(3, ce1);
                    ps.setDouble(4, ce2);
                    ps.setDouble(5, finalMarks);
                    ps.setDouble(6, attendance);
                    ps.setInt(7, recordId);
                    ps.executeUpdate();
                }
                loadAllPerformance();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Marks and attendance must be valid numbers.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error updating record: " + ex.getMessage());
            }
        }

        private void deleteRecord(int recordId) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete Record ID " + recordId + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) return;

            String sql = "DELETE FROM performance WHERE id=?";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, recordId);
                ps.executeUpdate();
                loadAllPerformance();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error deleting record: " + ex.getMessage());
            }
        }

        private void openVerificationDialog() {
            DefaultTableModel verificationModel = new DefaultTableModel(
                    new String[]{"Student ID", "Name", "Class", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            JTable verificationTable = new JTable(verificationModel);
            styleTable(verificationTable);

            String sql = "SELECT id, full_name, class_name, is_verified " +
                    "FROM users WHERE role='STUDENT' ORDER BY id ASC";
            try (Connection con = DB.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    verificationModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("class_name"),
                            rs.getBoolean("is_verified") ? "Verified" : "Pending"
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading verification list: " + ex.getMessage());
                return;
            }

            JScrollPane scrollPane = new JScrollPane(verificationTable);
            scrollPane.setPreferredSize(new Dimension(620, 280));
            int result = JOptionPane.showConfirmDialog(this, scrollPane, "Student Verification",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION || verificationTable.getSelectedRow() < 0) return;

            int row = verificationTable.getSelectedRow();
            int studentId = (int) verificationTable.getValueAt(row, 0);
            String status = verificationTable.getValueAt(row, 3).toString();
            if ("Verified".equals(status)) {
                JOptionPane.showMessageDialog(this, "This student is already verified.");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Verify selected student account?",
                    "Confirm Verification", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE users SET is_verified=TRUE WHERE id=? AND role='STUDENT'")) {
                ps.setInt(1, studentId);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Student account verified successfully.");
                loadStudents();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Verification failed: " + ex.getMessage());
            }
        }

        interface RowAction { void execute(int row); }

        static class TableButtonRenderer extends JButton implements TableCellRenderer {
            private static final long serialVersionUID = 1L;

            public TableButtonRenderer(String label, Color bg) {
                setText(label);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                setForeground(Color.WHITE);
                setBackground(bg);
                setFocusPainted(false);
                setBorderPainted(false);
                setOpaque(true);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                return this;
            }
        }

        static class TableButtonEditor extends DefaultCellEditor {
            private static final long serialVersionUID = 1L;

            private final JButton button;
            private int currentRow;

            public TableButtonEditor(JTable table, String label, Color bg, RowAction action) {
                super(new JCheckBox());
                button = new JButton(label);
                button.setFont(new Font("Segoe UI", Font.BOLD, 12));
                button.setForeground(Color.WHITE);
                button.setBackground(bg);
                button.setFocusPainted(false);
                button.setBorderPainted(false);
                button.setOpaque(true);
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));

                button.addActionListener(e -> {
                    int row = currentRow;
                    fireEditingStopped();
                    SwingUtilities.invokeLater(() -> action.execute(row));
                });
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                currentRow = row;
                return button;
            }

            @Override
            public Object getCellEditorValue() {
                return button.getText();
            }
        }
    }


    static class StudentDashboard extends JFrame {
        private static final long serialVersionUID = 1L;

        private final DefaultTableModel tableModel = new DefaultTableModel(
                new String[]{"Record ID", "Subject", "Mid Term", "CE1", "CE2", "Final",
                        "Paper Average", "Marks Risk", "Attendance (%)", "Attendance Risk", "Overall Risk"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        private final JLabel avgMarksVal = new JLabel("-");
        private final JLabel marksRiskStatus = new JLabel("N/A");
        private final JLabel avgAttVal = new JLabel("-");
        private final JLabel attRiskStatus = new JLabel("N/A");
        private final JLabel overallRiskVal = new JLabel("N/A");
        private final JLabel overallSubVal = new JLabel("Based on average of all subjects");

        public StudentDashboard(int studentId, String studentName) {
            setTitle("Student Dashboard - " + studentName);
            setSize(1080, 620);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout(15, 15));
            bg.setBorder(new EmptyBorder(18, 22, 22, 22));
            setContentPane(bg);

            // Top Navbar
            RoundedPanel nav = new RoundedPanel(16, CARD_BG);
            nav.setLayout(new BorderLayout());
            nav.setBorder(new EmptyBorder(12, 20, 12, 20));

            JLabel headerLbl = new JLabel("Student Dashboard  |  " + studentName + " (ID: " + studentId + ")");
            headerLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
            headerLbl.setForeground(TEXT_DARK);

            JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            navBtns.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            navBtns.add(bgBtn);

            JButton logoutBtn = createStyledButton("Logout", DANGER, DANGER_HOVER);
            logoutBtn.addActionListener(e -> {
                dispose();
                new LoginFrame().setVisible(true);
            });
            navBtns.add(logoutBtn);

            nav.add(headerLbl, BorderLayout.WEST);
            nav.add(navBtns, BorderLayout.EAST);

            // Center Panel
            JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
            centerPanel.setOpaque(false);

            // 3 Separate Risk Section Cards (Marks Risk | Attendance Risk | Overall Risk)
            JPanel riskGrid = new JPanel(new GridLayout(1, 3, 15, 0));
            riskGrid.setOpaque(false);
            riskGrid.add(createRiskSectionCard("Marks Risk Section (Avg / 100)", avgMarksVal, marksRiskStatus));
            riskGrid.add(createRiskSectionCard("Attendance Risk Section (Avg %)", avgAttVal, attRiskStatus));
            riskGrid.add(createRiskSectionCard("Overall Risk (Average of All)", overallRiskVal, overallSubVal));

            // Subject-wise Risks & Performance Table Card
            RoundedPanel tableCard = new RoundedPanel(16, CARD_BG);
            tableCard.setLayout(new BorderLayout(0, 8));
            tableCard.setBorder(new EmptyBorder(14, 14, 14, 14));

            JLabel tableTitle = new JLabel("Subject-Wise Performance & Risk Analysis");
            tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
            tableTitle.setForeground(TEXT_DARK);
            tableCard.add(tableTitle, BorderLayout.NORTH);

            JTable table = new JTable(tableModel);
            styleTable(table);

            // Colour-coded renderer for risk columns (3, 5, 6)
            DefaultTableCellRenderer riskCellRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                               boolean hasFocus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                    setHorizontalAlignment(JLabel.CENTER);
                    if (!isSelected) {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                    }
                    String text = val == null ? "" : val.toString();
                    c.setForeground(getRiskColor(text));
                    c.setFont(FONT_BOLD);
                    return c;
                }
            };
            table.getColumnModel().getColumn(7).setCellRenderer(riskCellRenderer);
            table.getColumnModel().getColumn(9).setCellRenderer(riskCellRenderer);
            table.getColumnModel().getColumn(10).setCellRenderer(riskCellRenderer);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            tableCard.add(scrollPane, BorderLayout.CENTER);

            // Risk Criteria Legend Bar
            RoundedPanel legendCard = new RoundedPanel(14, CARD_BG);
            legendCard.setLayout(new FlowLayout(FlowLayout.CENTER, 16, 8));
            JLabel legendLbl = new JLabel(
                    "<html><b>Risk Rules:</b> &nbsp; " +
                    "<font color='#DC2626'><b>HIGH RISK (Detain / Fail)</b></font>: Marks &lt; 40/100 or Attendance &lt; 75% &nbsp;|&nbsp; " +
                    "<font color='#D97706'><b>MEDIUM</b></font>: Marks 40–59 or Attendance 75–84% &nbsp;|&nbsp; " +
                    "<font color='#16A34A'><b>LOW (Safe)</b></font>: Marks &ge; 60 &amp; Attendance &ge; 85%</html>"
            );
            legendLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            legendLbl.setForeground(TEXT_DARK);
            legendCard.add(legendLbl);

            centerPanel.add(riskGrid, BorderLayout.NORTH);
            centerPanel.add(tableCard, BorderLayout.CENTER);
            centerPanel.add(legendCard, BorderLayout.SOUTH);

            bg.add(nav, BorderLayout.NORTH);
            bg.add(centerPanel, BorderLayout.CENTER);

            loadStudentData(studentId);
        }

        private RoundedPanel createRiskSectionCard(String title, JLabel mainVal, JLabel statusLbl) {
            RoundedPanel card = new RoundedPanel(16, CARD_BG);
            card.setLayout(new GridLayout(3, 1, 0, 4));
            card.setBorder(new EmptyBorder(14, 18, 14, 18));

            JLabel tLbl = new JLabel(title);
            tLbl.setFont(FONT_SUBTITLE);
            tLbl.setForeground(TEXT_MUTED);

            mainVal.setFont(new Font("Segoe UI", Font.BOLD, 20));
            mainVal.setForeground(TEXT_DARK);

            statusLbl.setFont(FONT_BOLD);
            statusLbl.setForeground(TEXT_MUTED);

            card.add(tLbl);
            card.add(mainVal);
            card.add(statusLbl);
            return card;
        }

        private void loadStudentData(int studentId) {
            tableModel.setRowCount(0);
            double totalMarks = 0, totalAttendance = 0;
            int count = 0;

            String sql = "SELECT id, subject, mid_term_marks, ce1_marks, ce2_marks, final_marks, attendance " +
                    "FROM performance WHERE student_id=? ORDER BY id DESC";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    double midTerm = rs.getDouble("mid_term_marks");
                    double ce1 = rs.getDouble("ce1_marks");
                    double ce2 = rs.getDouble("ce2_marks");
                    double finalMarks = rs.getDouble("final_marks");
                    double m = (midTerm + ce1 + ce2 + finalMarks) / 4.0;
                    double a = rs.getDouble("attendance");

                    String mRisk = getMarksRisk(m);
                    String aRisk = getAttendanceRisk(a);
                    String combined = getCombinedRisk(m, a);

                    tableModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("subject"),
                            String.format("%.1f", midTerm),
                            String.format("%.1f", ce1),
                            String.format("%.1f", ce2),
                            String.format("%.1f", finalMarks),
                            String.format("%.1f / 100", m),
                            mRisk,
                            String.format("%.1f%%", a),
                            aRisk,
                            combined
                    });

                    totalMarks += m;
                    totalAttendance += a;
                    count++;
                }

                if (count == 0) {
                    avgMarksVal.setText("0.0 / 100");
                    marksRiskStatus.setText("No records yet");
                    avgAttVal.setText("0.0%");
                    attRiskStatus.setText("No records yet");
                    overallRiskVal.setText("N/A");
                    return;
                }

                double avgMarks = totalMarks / count;
                double avgAttendance = totalAttendance / count;

                String overallMRisk = getMarksRisk(avgMarks);
                String overallARisk = getAttendanceRisk(avgAttendance);
                String overallCombined = getCombinedRisk(avgMarks, avgAttendance);

                avgMarksVal.setText(String.format("%.1f / 100", avgMarks));
                marksRiskStatus.setText("Status: " + overallMRisk);
                marksRiskStatus.setForeground(getRiskColor(overallMRisk));

                avgAttVal.setText(String.format("%.1f%%", avgAttendance));
                attRiskStatus.setText("Status: " + overallARisk);
                attRiskStatus.setForeground(getRiskColor(overallARisk));

                overallRiskVal.setText(overallCombined);
                overallRiskVal.setForeground(getRiskColor(overallCombined));
                overallSubVal.setText(String.format("Avg across %d subject(s)", count));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading data: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        DB.initializeSchema();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}