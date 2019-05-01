package jadx.gui.ui.codearea;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.tree.TreeNode;

import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JResource;
import jadx.gui.ui.ContentPanel;
import jadx.gui.ui.TabbedPane;
import jadx.gui.utils.NLS;
import jadx.gui.utils.Utils;

public final class CodePanel extends AbstractCodePanel {
	private static final long serialVersionUID = 5310536092010045565L;

	private final SearchBar searchBar;
	private final CodeArea codeArea;
	private final JScrollPane codeScrollPane;

	public CodePanel(TabbedPane panel, JNode jnode) {
		super(panel, jnode);

		codeArea = new CodeArea(this);
		searchBar = new SearchBar(codeArea);
		codeScrollPane = new JScrollPane(codeArea);
		initLineNumbers();

		setLayout(new BorderLayout());
		add(searchBar, BorderLayout.NORTH);
		add(codeScrollPane);

		KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_F, Utils.ctrlButton());
		SearchAction searchAction = new SearchAction(searchBar);
		Utils.addKeyBinding(codeArea, key, "SearchAction", searchAction);
	}

	private void initLineNumbers() {
		// TODO: fix slow line rendering on big files
		if (codeArea.getDocument().getLength() <= 100_000) {
			LineNumbers numbers = new LineNumbers(codeArea);
			numbers.setUseSourceLines(isUseSourceLines());
			codeScrollPane.setRowHeaderView(numbers);
		}
	}

	private boolean isUseSourceLines() {
		if (node instanceof JResource) {
			JResource resNode = (JResource) node;
			return !resNode.getLineMapping().isEmpty();
		}
		return false;
	}

	@Override
	public void loadSettings() {
		codeArea.loadSettings();
		initLineNumbers();
		updateUI();
	}

	@Override
	public TabbedPane getTabbedPane() {
		return tabbedPane;
	}

	@Override
	public JNode getNode() {
		return node;
	}

	SearchBar getSearchBar() {
		return searchBar;
	}

	@Override
	public CodeArea getCodeArea() {
		return codeArea;
	}

	@Override
	public String getTabTooltip() {
		String s = node.getName();
		JNode n = (JNode) node.getParent();
		while (n != null) {
			String name = n.getName();
			if (name == null) {
				break;
			}
			s = name + '/' + s;
			n = (JNode) n.getParent();
		}
		return '/' + s;
	}
}
