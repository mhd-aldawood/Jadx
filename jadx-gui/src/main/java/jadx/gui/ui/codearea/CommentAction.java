package jadx.gui.ui.codearea;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.event.PopupMenuEvent;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.data.ICodeComment;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRef;
import jadx.api.data.impl.JadxNodeRef;
import jadx.api.metadata.ICodeAnnotation;
import jadx.api.metadata.ICodeMetadata;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.metadata.annotations.InsnCodeOffset;
import jadx.api.metadata.annotations.NodeDeclareRef;
import jadx.api.metadata.annotations.VarRef;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.dialog.CommentDialog;
import jadx.gui.utils.DefaultPopupMenuListener;
import jadx.gui.utils.NLS;
import jadx.gui.utils.UiUtils;

import static javax.swing.KeyStroke.getKeyStroke;

public class CommentAction extends AbstractAction implements DefaultPopupMenuListener {
	private static final long serialVersionUID = 4753838562204629112L;

	private static final Logger LOG = LoggerFactory.getLogger(CommentAction.class);
	private final CodeArea codeArea;
	private final JavaClass topCls;

	private ICodeComment actionComment;

	public CommentAction(CodeArea codeArea) {
		super(NLS.str("popup.add_comment") + " (;)");
		this.codeArea = codeArea;
		JNode topNode = codeArea.getNode();
		if (topNode instanceof JClass) {
			this.topCls = ((JClass) topNode).getCls();
		} else {
			this.topCls = null;
		}
		UiUtils.addKeyBinding(codeArea, getKeyStroke(KeyEvent.VK_SEMICOLON, 0), "popup.add_comment",
				() -> showCommentDialog(getCommentRef(codeArea.getCaretPosition())));
	}

	@Override
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		ICodeComment codeComment = getCommentRef(UiUtils.getOffsetAtMousePosition(codeArea));
		setEnabled(codeComment != null);
		this.actionComment = codeComment;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		showCommentDialog(this.actionComment);
	}

	private void showCommentDialog(ICodeComment codeComment) {
		if (codeComment == null) {
			UiUtils.showMessageBox(codeArea.getMainWindow(), NLS.str("msg.cant_add_comment"));
			return;
		}
		CommentDialog.show(codeArea, codeComment);
	}

	/**
	 * Check if possible insert comment at current line.
	 *
	 * @return blank code comment object (comment string empty)
	 */
	@Nullable
	private ICodeComment getCommentRef(int pos) {
		if (pos == -1 || this.topCls == null) {
			return null;
		}
		try {
			JadxDecompiler decompiler = codeArea.getDecompiler();
			ICodeMetadata metadata = codeArea.getCodeInfo().getCodeMetadata();
			// add comment if node definition at this line
			ICodeAnnotation ann = metadata.getAt(pos);
			if (ann instanceof NodeDeclareRef) {
				ICodeNodeRef node = ((NodeDeclareRef) ann).getNode();
				if (!(node instanceof VarRef)) {
					// at node definition -> add comment for it
					JadxNodeRef nodeRef = JadxNodeRef.forJavaNode(decompiler.getJavaNodeByRef(node));
					return new JadxCodeComment(nodeRef, "");
				}
			}
			if (ann == null) {
				// check if line with comment above node definition
				try {
					JavaNode defNode = decompiler.getJavaNodeByRef(metadata.getNodeBelow(pos));
					if (defNode != null) {
						String lineStr = codeArea.getLineAt(pos).trim();
						if (lineStr.startsWith("//")) {
							return new JadxCodeComment(JadxNodeRef.forJavaNode(defNode), "");
						}
					}
				} catch (Exception e) {
					LOG.error("Failed to check comment at: " + pos, e);
				}
				return null;
			}

			// try to add method line comment
			JavaNode node = decompiler.getJavaNodeByRef(metadata.getNodeAt(pos));
			if (node instanceof JavaMethod) {
				JadxNodeRef nodeRef = JadxNodeRef.forMth((JavaMethod) node);
				if (ann instanceof InsnCodeOffset) {
					int rawOffset = ((InsnCodeOffset) ann).getOffset();
					return new JadxCodeComment(nodeRef, JadxCodeRef.forInsn(rawOffset), "");
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to add comment at: " + pos, e);
		}
		return null;
	}
}
