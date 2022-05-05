package jadx.gui.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JResSearchNode;
import jadx.gui.ui.MainWindow;
import jadx.gui.ui.TabbedPane;
import jadx.gui.ui.codearea.AbstractCodeArea;
import jadx.gui.ui.panel.ProgressPanel;
import jadx.gui.utils.CacheObject;
import jadx.gui.utils.JNodeCache;
import jadx.gui.utils.JumpPosition;
import jadx.gui.utils.NLS;
import jadx.gui.utils.UiUtils;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public abstract class CommonSearchDialog extends JFrame {
	private static final long serialVersionUID = 8939332306115370276L;

	protected final transient TabbedPane tabbedPane;
	protected final transient CacheObject cache;
	protected final transient MainWindow mainWindow;
	protected final transient Font codeFont;
	protected final transient String windowTitle;

	protected ResultsModel resultsModel;
	protected ResultsTable resultsTable;
	protected JLabel resultsInfoLabel;
	protected JLabel warnLabel;
	protected ProgressPanel progressPane;

	private String highlightText;
	protected boolean highlightTextCaseInsensitive = false;
	protected boolean highlightTextUseRegex = false;

	public CommonSearchDialog(MainWindow mainWindow, String title) {
		this.mainWindow = mainWindow;
		this.tabbedPane = mainWindow.getTabbedPane();
		this.cache = mainWindow.getCacheObject();
		this.codeFont = mainWindow.getSettings().getFont();
		this.windowTitle = title;
		UiUtils.setWindowIcons(this);
		updateTitle();
	}

	protected abstract void openInit();

	protected abstract void loadFinished();

	protected abstract void loadStart();

	public void loadWindowPos() {
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			setSize(800, 500);
		}
	}

	private void updateTitle() {
		if (highlightText == null || highlightText.trim().isEmpty()) {
			setTitle(windowTitle);
		} else {
			setTitle(windowTitle + ": " + highlightText);
		}
	}

	public void setHighlightText(String highlightText) {
		this.highlightText = highlightText;
		updateTitle();
	}

	protected void registerInitOnOpen() {
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				SwingUtilities.invokeLater(CommonSearchDialog.this::openInit);
			}
		});
	}

	protected synchronized void performSearch() {
		resultsTable.updateTable();
		updateProgressLabel(true);
	}

	protected void openSelectedItem() {
		JNode node = getSelectedNode();
		if (node == null) {
			return;
		}
		openItem(node);
	}

	protected void openItem(JNode node) {
		if (node instanceof JResSearchNode) {
			JumpPosition jmpPos = new JumpPosition(((JResSearchNode) node).getResNode(), node.getPos());
			tabbedPane.codeJump(jmpPos);
		} else {
			tabbedPane.codeJump(node);
		}
		if (!mainWindow.getSettings().getKeepCommonDialogOpen()) {
			dispose();
		}
	}

	@Nullable
	private JNode getSelectedNode() {
		int selectedId = resultsTable.getSelectedRow();
		if (selectedId == -1) {
			return null;
		}
		return (JNode) resultsModel.getValueAt(selectedId, 0);
	}

	@Override
	public void dispose() {
		mainWindow.getSettings().saveWindowPos(this);
		super.dispose();
	}

	protected void initCommon() {
		UiUtils.addEscapeShortCutToDispose(this);
	}

	@NotNull
	protected JPanel initButtonsPanel() {
		progressPane = new ProgressPanel(mainWindow, false);

		JButton cancelButton = new JButton(NLS.str("search_dialog.cancel"));
		cancelButton.addActionListener(event -> dispose());
		JButton openBtn = new JButton(NLS.str("search_dialog.open"));
		openBtn.addActionListener(event -> openSelectedItem());
		getRootPane().setDefaultButton(openBtn);

		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
		buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

		JCheckBox cbKeepOpen = new JCheckBox(NLS.str("search_dialog.keep_open"));
		cbKeepOpen.setSelected(mainWindow.getSettings().getKeepCommonDialogOpen());
		cbKeepOpen.addActionListener(e -> {
			mainWindow.getSettings().setKeepCommonDialogOpen(cbKeepOpen.isSelected());
			mainWindow.getSettings().sync();
		});
		buttonPane.add(cbKeepOpen);
		buttonPane.add(Box.createRigidArea(new Dimension(15, 0)));
		buttonPane.add(progressPane);
		buttonPane.add(Box.createRigidArea(new Dimension(5, 0)));
		buttonPane.add(Box.createHorizontalGlue());
		buttonPane.add(openBtn);
		buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
		buttonPane.add(cancelButton);
		return buttonPane;
	}

	protected JPanel initResultsTable() {
		ResultsTableCellRenderer renderer = new ResultsTableCellRenderer();
		resultsModel = new ResultsModel(renderer);
		resultsModel.addTableModelListener(e -> updateProgressLabel(false));

		resultsTable = new ResultsTable(resultsModel, renderer);
		resultsTable.setShowHorizontalLines(false);
		resultsTable.setDragEnabled(false);
		resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultsTable.setColumnSelectionAllowed(false);
		resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		resultsTable.setAutoscrolls(false);

		resultsTable.setDefaultRenderer(Object.class, renderer);
		Enumeration<TableColumn> columns = resultsTable.getColumnModel().getColumns();
		while (columns.hasMoreElements()) {
			TableColumn column = columns.nextElement();
			column.setCellRenderer(renderer);
		}

		resultsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					openSelectedItem();
				}
			}
		});
		resultsTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					openSelectedItem();
				}
			}
		});
		// override copy action to copy long string of node column
		resultsTable.getActionMap().put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JNode selectedNode = getSelectedNode();
				if (selectedNode != null) {
					UiUtils.copyToClipboard(selectedNode.makeLongString());
				}
			}
		});

		warnLabel = new JLabel();
		warnLabel.setForeground(Color.RED);
		warnLabel.setVisible(false);

		JScrollPane scroll = new JScrollPane(resultsTable, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
		// scroll.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		JPanel resultsActionsPanel = new JPanel();
		resultsActionsPanel.setLayout(new BoxLayout(resultsActionsPanel, BoxLayout.LINE_AXIS));
		resultsActionsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
		addCustomResultsActions(resultsActionsPanel);
		resultsInfoLabel = new JLabel("");
		resultsInfoLabel.setFont(mainWindow.getSettings().getFont());
		resultsActionsPanel.add(Box.createRigidArea(new Dimension(20, 0)));
		resultsActionsPanel.add(resultsInfoLabel);
		resultsActionsPanel.add(Box.createHorizontalGlue());

		JPanel resultsPanel = new JPanel();
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.PAGE_AXIS));
		resultsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		resultsPanel.add(warnLabel, BorderLayout.PAGE_START);
		resultsPanel.add(scroll, BorderLayout.CENTER);
		resultsPanel.add(resultsActionsPanel, BorderLayout.PAGE_END);
		return resultsPanel;
	}

	protected void addCustomResultsActions(JPanel actionsPanel) {
	}

	protected void updateProgressLabel(boolean complete) {
		int count = resultsModel.getRowCount();
		String statusText;
		if (complete) {
			statusText = NLS.str("search_dialog.results_complete", count);
		} else {
			statusText = NLS.str("search_dialog.results_incomplete", count);
		}
		resultsInfoLabel.setText(statusText);
	}

	protected void showSearchState() {
		resultsInfoLabel.setText(NLS.str("search_dialog.tip_searching") + "...");
	}

	protected static class ResultsTable extends JTable {
		private static final long serialVersionUID = 3901184054736618969L;
		private final transient ResultsTableCellRenderer renderer;

		public ResultsTable(ResultsModel resultsModel, ResultsTableCellRenderer renderer) {
			super(resultsModel);
			this.renderer = renderer;
		}

		public void updateTable() {
			ResultsModel model = (ResultsModel) getModel();
			TableColumnModel columnModel = getColumnModel();

			int width = getParent().getWidth();
			int firstColMaxWidth = (int) (width * 0.5);
			int rowCount = getRowCount();
			int columnCount = getColumnCount();
			boolean addDescColumn = model.isAddDescColumn();
			if (!addDescColumn) {
				firstColMaxWidth = width;
			}
			setRowHeight(10); // reset all rows height
			for (int col = 0; col < columnCount; col++) {
				int colWidth = 50;
				for (int row = 0; row < rowCount; row++) {
					Component comp = prepareRenderer(renderer, row, col);
					if (comp == null) {
						continue;
					}
					colWidth = Math.max(comp.getPreferredSize().width, colWidth);
					int h = Math.max(getRowHeight(row), getHeight(comp));
					if (h > 1) {
						setRowHeight(row, h);
					}
				}
				colWidth += 10;
				if (col == 0) {
					colWidth = Math.min(colWidth, firstColMaxWidth);
				} else {
					colWidth = Math.max(colWidth, width - columnModel.getColumn(0).getPreferredWidth());
				}
				TableColumn column = columnModel.getColumn(col);
				column.setPreferredWidth(colWidth);
			}
			updateUI();
		}

		private int getHeight(@Nullable Component nodeComp) {
			if (nodeComp != null) {
				return Math.max(nodeComp.getHeight(), nodeComp.getPreferredSize().height);
			}
			return 0;
		}
	}

	protected static class ResultsModel extends AbstractTableModel {
		private static final long serialVersionUID = -7821286846923903208L;
		private static final String[] COLUMN_NAMES = { NLS.str("search_dialog.col_node"), NLS.str("search_dialog.col_code") };

		private final transient List<JNode> rows = new ArrayList<>();
		private final transient ResultsTableCellRenderer renderer;
		private transient boolean addDescColumn;

		public ResultsModel(ResultsTableCellRenderer renderer) {
			this.renderer = renderer;
		}

		protected void addAll(Collection<? extends JNode> nodes) {
			rows.addAll(nodes);
			if (!addDescColumn) {
				for (JNode row : rows) {
					if (row.hasDescString()) {
						addDescColumn = true;
						break;
					}
				}
			}
		}

		protected void add(JNode node) {
			rows.add(node);
			if (!addDescColumn && node.hasDescString()) {
				addDescColumn = true;
			}
		}

		public void removeLast() {
			rows.remove(rows.size() - 1);
		}

		public void clear() {
			addDescColumn = false;
			rows.clear();
			renderer.clear();
		}

		public boolean isAddDescColumn() {
			return addDescColumn;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public String getColumnName(int index) {
			return COLUMN_NAMES[index];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return rows.get(rowIndex);
		}
	}

	protected class ResultsTableCellRenderer implements TableCellRenderer {
		private final JLabel emptyLabel = new JLabel();
		private final Font font;
		private final Color codeSelectedColor;
		private final Color codeBackground;
		private final Map<Integer, Component> componentCache = new HashMap<>();

		public ResultsTableCellRenderer() {
			RSyntaxTextArea area = AbstractCodeArea.getDefaultArea(mainWindow);
			this.font = area.getFont();
			this.codeSelectedColor = area.getSelectionColor();
			this.codeBackground = area.getBackground();
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row,
				int column) {
			int id = makeID(row, column);
			Component comp = componentCache.get(id);
			if (comp == null) {
				if (obj instanceof JNode) {
					comp = makeCell((JNode) obj, column);
					componentCache.put(id, comp);
				} else {
					comp = emptyLabel;
				}
			}
			updateSelection(table, comp, isSelected);
			return comp;
		}

		private int makeID(int row, int col) {
			return row << 2 | col;
		}

		private void updateSelection(JTable table, Component comp, boolean isSelected) {
			if (comp instanceof RSyntaxTextArea) {
				if (isSelected) {
					comp.setBackground(codeSelectedColor);
				} else {
					comp.setBackground(codeBackground);
				}
			} else {
				if (isSelected) {
					comp.setBackground(table.getSelectionBackground());
					comp.setForeground(table.getSelectionForeground());
				} else {
					comp.setBackground(table.getBackground());
					comp.setForeground(table.getForeground());
				}
			}
		}

		private Component makeCell(JNode node, int column) {
			if (column == 0) {
				JLabel label = new JLabel(node.makeLongStringHtml() + "  ", node.getIcon(), SwingConstants.LEFT);
				label.setFont(font);
				label.setOpaque(true);
				label.setToolTipText(label.getText());
				return label;
			}
			if (!node.hasDescString()) {
				return emptyLabel;
			}

			RSyntaxTextArea textArea = AbstractCodeArea.getDefaultArea(mainWindow);
			textArea.setSyntaxEditingStyle(node.getSyntaxName());
			String descStr = node.makeDescString();
			textArea.setText(descStr);
			if (descStr.contains("\n")) {
				textArea.setRows(textArea.getLineCount());
			} else {
				textArea.setRows(1);
				textArea.setColumns(descStr.length() + 1);
			}
			if (highlightText != null) {
				SearchContext searchContext = new SearchContext(highlightText);
				searchContext.setMatchCase(!highlightTextCaseInsensitive);
				searchContext.setRegularExpression(highlightTextUseRegex);
				searchContext.setMarkAll(true);
				SearchEngine.markAll(textArea, searchContext);
			}
			textArea.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
			return textArea;
		}

		public void clear() {
			componentCache.clear();
		}
	}

	void progressStartCommon() {
		// setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		progressPane.setIndeterminate(true);
		progressPane.setVisible(true);
		warnLabel.setVisible(false);
	}

	void progressFinishedCommon() {
		// setCursor(null);
		progressPane.setVisible(false);
	}

	protected JNodeCache getNodeCache() {
		return mainWindow.getCacheObject().getNodeCache();
	}
}
