package jadx.gui.ui.codearea;

import jadx.gui.treemodel.JResource;
import jadx.gui.ui.ContentPanel;
import jadx.gui.utils.Utils;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * A panel combining a {@link SearchBar and a scollable {@link CodeArea}
 */
public class CodePanel extends JPanel {

	private final SearchBar searchBar;
	private final AbstractCodeArea codeArea;
	private final JScrollPane codeScrollPane;

	public CodePanel(ContentPanel contentPanel) {
		super();

		codeArea = createCodeArea(contentPanel);
		searchBar = new SearchBar(codeArea);
		codeScrollPane = new JScrollPane(codeArea);

		setLayout(new BorderLayout());
		add(searchBar, BorderLayout.NORTH);
		add(codeScrollPane, BorderLayout.CENTER);
		initLineNumbers();

		KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_F, Utils.ctrlButton());
		Utils.addKeyBinding(codeArea, key, "SearchAction", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchBar.toggle();
			}
		});
	}

	protected AbstractCodeArea createCodeArea(ContentPanel contentPanel) {
		return new CodeArea(contentPanel);
	}

	public void loadSettings() {
		codeArea.loadSettings();
		initLineNumbers();
	}

	public void load() {
		codeArea.load();
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
		if (codeArea.getNode() instanceof JResource) {
			JResource resNode = (JResource) codeArea.getNode();
			return !resNode.getLineMapping().isEmpty();
		}
		return false;
	}

	public SearchBar getSearchBar() {
		return searchBar;
	}

	public AbstractCodeArea getCodeArea() {
		return codeArea;
	}

	public JScrollPane getCodeScrollPane() {
		return codeScrollPane;
	}
}
