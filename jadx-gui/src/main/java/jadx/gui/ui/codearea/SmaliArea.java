package jadx.gui.ui.codearea;

import jadx.gui.ui.ContentPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import jadx.gui.treemodel.JNode;

public final class SmaliArea extends RSyntaxTextArea {
	private static final long serialVersionUID = 1334485631870306494L;

	private final JNode node;

	SmaliArea(ContentPanel panel) {
		node = panel.getNode();

		setEditable(false);
	}

	void load() {
		if (getText().isEmpty()) {
			setText(node.getSmali());
			setCaretPosition(0);
		}
	}
}
