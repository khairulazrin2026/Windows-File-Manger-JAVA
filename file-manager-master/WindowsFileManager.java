//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A small Windows-style file manager written with Java Swing.
 * Java 17+ recommended.
 * Copyright (c) 2026 KA PC Network Services
 **/


public class WindowsFileManager extends JFrame {
    private final JTree tree = new JTree();
    private final FileTableModel tableModel = new FileTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField pathField = new JTextField();
    private final JTextField searchField = new JTextField(18);
    private final JLabel status = new JLabel("Ready");
    private final JButton upButton = new JButton("Up");
    private final JButton pasteButton = new JButton("Paste");

    private Path currentDirectory;
    private final List<Path> clipboard = new ArrayList<>();
    private boolean clipboardCut;

    public WindowsFileManager() {
        super("Java File Manager");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        buildUi();
        loadTree();

        Path home = Paths.get(System.getProperty("user.home"));
        setCurrentDirectory(Files.isDirectory(home) ? home : Paths.get("C:\\"));
    }

    private void buildUi() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton backButton = new JButton("Back");
        JButton forwardButton = new JButton("Forward");
        JButton newFolderButton = new JButton("New folder");
        JButton renameButton = new JButton("Rename");
        JButton deleteButton = new JButton("Delete");
        JButton copyButton = new JButton("Copy");
        JButton cutButton = new JButton("Cut");
        JButton refreshButton = new JButton("Refresh");

        // Back/forward are placeholders for a small starter project.
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);

        toolbar.add(backButton);
        toolbar.add(forwardButton);
        toolbar.add(upButton);
        toolbar.addSeparator();
        toolbar.add(newFolderButton);
        toolbar.add(renameButton);
        toolbar.add(deleteButton);
        toolbar.addSeparator();
        toolbar.add(copyButton);
        toolbar.add(cutButton);
        toolbar.add(pasteButton);
        toolbar.add(refreshButton);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(new JLabel(" Search: "));
        toolbar.add(searchField);
        JButton searchButton = new JButton("Go");
        toolbar.add(searchButton);

        pathField.setEditable(false);
        pathField.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        pasteButton.setEnabled(false);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new FileTreeRenderer());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override public void treeWillExpand(TreeExpansionEvent event) {
                Object last = event.getPath().getLastPathComponent();
                if (last instanceof FileTreeNode node) node.loadChildren();
            }
            @Override public void treeWillCollapse(TreeExpansionEvent event) { }
        });
        tree.addTreeSelectionListener(e -> {
            Object selected = tree.getLastSelectedPathComponent();
            if (selected instanceof FileTreeNode node) setCurrentDirectory(node.file);
        });

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setShowGrid(false);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(430);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected();
            }
        });

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree),
                new JScrollPane(table));
        splitPane.setDividerLocation(260);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar, BorderLayout.NORTH);
        topPanel.add(pathField, BorderLayout.SOUTH);

        setLayout(new BorderLayout(5, 5));
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        upButton.addActionListener(e -> goUp());
        newFolderButton.addActionListener(e -> createFolder());
        renameButton.addActionListener(e -> renameSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        copyButton.addActionListener(e -> copySelected(false));
        cutButton.addActionListener(e -> copySelected(true));
        pasteButton.addActionListener(e -> paste());
        refreshButton.addActionListener(e -> refresh());
        searchButton.addActionListener(e -> search());
        searchField.addActionListener(e -> search());

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "open");
        table.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openSelected(); }
        });
    }

    private void loadTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("This PC");
        for (File file : File.listRoots()) {
            if (file.isDirectory()) root.add(new FileTreeNode(file.toPath()));
        }
        tree.setModel(new DefaultTreeModel(root));
    }

    private void setCurrentDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        currentDirectory = directory.toAbsolutePath().normalize();
        pathField.setText(currentDirectory.toString());
        upButton.setEnabled(currentDirectory.getParent() != null);
        searchField.setText("");
        showDirectoryContents();
        status.setText(tableModel.getRowCount() + " item(s)");
    }

    private void showDirectoryContents() {
        try {
            List<File> files;
            try (var stream = Files.list(currentDirectory)) {
                files = stream.map(Path::toFile).sorted((a, b) -> {
                    int directoryOrder = Boolean.compare(!a.isDirectory(), !b.isDirectory());
                    return directoryOrder != 0
                            ? directoryOrder
                            : a.getName().compareToIgnoreCase(b.getName());
                }).collect(Collectors.toList());
            }
            tableModel.setFiles(files);
        } catch (IOException | SecurityException ex) {
            tableModel.setFiles(List.of());
            showError("Cannot read folder: " + ex.getMessage());
        }
    }

    private void refresh() {
        loadTree();
        if (currentDirectory != null) showDirectoryContents();
        status.setText("Refreshed");
    }

    private void goUp() {
        if (currentDirectory != null && currentDirectory.getParent() != null) {
            setCurrentDirectory(currentDirectory.getParent());
        }
    }

    private List<Path> selectedPaths() {
        int[] selectedRows = table.getSelectedRows();
        List<Path> result = new ArrayList<>();
        for (int viewRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            result.add(tableModel.getFile(modelRow).toPath());
        }
        return result;
    }

    private void openSelected() {
        List<Path> selected = selectedPaths();
        if (selected.size() != 1) return;
        Path path = selected.get(0);
        try {
            if (Files.isDirectory(path)) setCurrentDirectory(path);
            else if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile());
            else showError("Desktop file opening is not supported on this system.");
        } catch (IOException | SecurityException ex) {
            showError("Cannot open item: " + ex.getMessage());
        }
    }

    private void createFolder() {
        if (currentDirectory == null) return;
        String name = JOptionPane.showInputDialog(this, "Folder name:", "New folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        try {
            Files.createDirectory(currentDirectory.resolve(name.trim()));
            refresh();
        } catch (IOException | InvalidPathException | SecurityException ex) {
            showError("Cannot create folder: " + ex.getMessage());
        }
    }

    private void renameSelected() {
        List<Path> selected = selectedPaths();
        if (selected.size() != 1) return;
        Path oldPath = selected.get(0);
        String newName = JOptionPane.showInputDialog(this, "New name:", oldPath.getFileName().toString());
        if (newName == null || newName.isBlank()) return;
        try {
            Files.move(oldPath, oldPath.resolveSibling(newName.trim()));
            refresh();
        } catch (IOException | InvalidPathException | SecurityException ex) {
            showError("Cannot rename item: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        List<Path> selected = selectedPaths();
        if (selected.isEmpty()) return;
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete " + selected.size() + " selected item(s)?\nThis cannot be undone.",
                "Confirm delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        try {
            for (Path path : selected) deleteRecursively(path);
            refresh();
        } catch (IOException | SecurityException ex) {
            showError("Cannot delete item: " + ex.getMessage());
        }
    }

    private void copySelected(boolean cut) {
        clipboard.clear();
        clipboard.addAll(selectedPaths());
        clipboardCut = cut;
        pasteButton.setEnabled(!clipboard.isEmpty());
        status.setText(clipboard.size() + " item(s) " + (cut ? "cut" : "copied") + ".");
    }

    private void paste() {
        if (currentDirectory == null || clipboard.isEmpty()) return;
        try {
            for (Path source : new ArrayList<>(clipboard)) {
                Path target = currentDirectory.resolve(source.getFileName());
                if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                    throw new IOException("Source and destination are the same.");
                }
                if (Files.exists(target)) throw new IOException("Destination already exists: " + target.getFileName());
                if (clipboardCut) {
                    try {
                        Files.move(source, target);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(source, target);
                    }
                } else {
                    copyRecursively(source, target);
                }
            }
            if (clipboardCut) {
                clipboard.clear();
                pasteButton.setEnabled(false);
            }
            refresh();
        } catch (IOException | SecurityException ex) {
            showError("Paste failed: " + ex.getMessage());
        }
    }

    private void search() {
        if (currentDirectory == null) return;
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            showDirectoryContents();
            status.setText(tableModel.getRowCount() + " item(s)");
            return;
        }

        status.setText("Searching...");
        Path startingDirectory = currentDirectory;
        new SwingWorker<List<File>, Void>() {
            @Override protected List<File> doInBackground() throws Exception {
                try (var stream = Files.walk(startingDirectory)) {
                    return stream.filter(path -> !path.equals(startingDirectory))
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
                            .map(Path::toFile)
                            .limit(5000)
                            .collect(Collectors.toList());
                }
            }
            @Override protected void done() {
                try {
                    tableModel.setFiles(get());
                    status.setText(tableModel.getRowCount() + " search result(s)");
                } catch (Exception ex) {
                    showError("Search failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (var stream = Files.list(source)) {
                for (Path child : stream.collect(Collectors.toList())) {
                    copyRecursively(child, target.resolve(child.getFileName()));
                }
            }
        } else {
            Files.copy(source, target);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.collect(Collectors.toList())) deleteRecursively(child);
            }
        }
        Files.delete(path);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "File Manager", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WindowsFileManager().setVisible(true));
    }

    private static class FileTreeNode extends DefaultMutableTreeNode {
        private final Path file;
        private boolean loaded;

        FileTreeNode(Path file) {
            super(file.getFileName() == null ? file.toString() : file.getFileName().toString());
            this.file = file;
            add(new DefaultMutableTreeNode("Loading..."));
        }

        void loadChildren() {
            if (loaded) return;
            loaded = true;
            removeAllChildren();
            try (var stream = Files.list(file)) {
                stream.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> add(new FileTreeNode(path)));
            } catch (IOException | SecurityException ignored) { }
        }
    }

    private static class FileTreeRenderer extends DefaultTreeCellRenderer {
        @Override public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof FileTreeNode node) setText(node.file.toString());
            return this;
        }
    }

    private static class FileTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Type", "Size", "Modified"};
        private final List<File> files = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        void setFiles(List<File> newFiles) {
            files.clear();
            files.addAll(newFiles);
            fireTableDataChanged();
        }

        File getFile(int row) { return files.get(row); }
        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override public Object getValueAt(int row, int column) {
            File file = files.get(row);
            return switch (column) {
                case 0 -> file.getName();
                case 1 -> file.isDirectory() ? "Folder" : getExtension(file.getName());
                case 2 -> file.isDirectory() ? "" : formatSize(file.length());
                case 3 -> dateFormat.format(new Date(file.lastModified()));
                default -> "";
            };
        }

        private static String getExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) + " file" : "File";
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
            return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
