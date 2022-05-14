package jadx.gui.search.providers;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jadx.api.ICodeCache;
import jadx.api.ICodeWriter;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaNode;
import jadx.api.metadata.ICodeMetadata;
import jadx.api.metadata.ICodeNodeRef;
import jadx.gui.jobs.Cancelable;
import jadx.gui.search.SearchSettings;
import jadx.gui.treemodel.CodeNode;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;

public final class CodeSearchProvider extends BaseSearchProvider {

	private final ICodeCache codeCache;
	private final JadxDecompiler decompiler;

	private @Nullable String code;
	private int clsNum = 0;
	private int pos = 0;

	public CodeSearchProvider(MainWindow mw, SearchSettings searchSettings, List<JavaClass> classes) {
		super(mw, searchSettings, classes);
		this.codeCache = mw.getWrapper().getArgs().getCodeCache();
		this.decompiler = mw.getWrapper().getDecompiler();
	}

	@Override
	public @Nullable JNode next(Cancelable cancelable) {
		while (true) {
			if (cancelable.isCanceled()) {
				return null;
			}
			JavaClass cls = classes.get(clsNum);
			if (!cls.getClassNode().isInner()) {
				if (code == null) {
					code = getClassCode(cls, codeCache);
				}
				JNode newResult = searchNext(cls, code);
				if (newResult != null) {
					return newResult;
				}
			}
			if (nextClass()) {
				return null;
			}
		}
	}

	@Nullable
	private JNode searchNext(JavaClass javaClass, String clsCode) {
		int newPos = searchMth.find(clsCode, searchStr, pos);
		if (newPos == -1) {
			return null;
		}
		int lineStart = 1 + clsCode.lastIndexOf(ICodeWriter.NL, newPos);
		int lineEnd = clsCode.indexOf(ICodeWriter.NL, newPos);
		int end = lineEnd == -1 ? clsCode.length() : lineEnd;
		String line = clsCode.substring(lineStart, end);
		this.pos = end;
		return new CodeNode(getEnclosingNode(javaClass, end), line.trim(), newPos);
	}

	private JNode getEnclosingNode(JavaClass javaCls, int pos) {
		ICodeMetadata metadata = javaCls.getCodeInfo().getCodeMetadata();
		ICodeNodeRef nodeRef = metadata.getNodeAt(pos);
		JavaNode encNode = decompiler.getJavaNodeByRef(nodeRef);
		if (encNode == null) {
			return convert(javaCls);
		}
		return convert(encNode);
	}

	private String getClassCode(JavaClass javaClass, ICodeCache codeCache) {
		// quick check for if code already in cache
		String code = codeCache.getCode(javaClass.getRawName());
		if (code != null) {
			return code;
		}
		return javaClass.getCode();
	}

	private boolean nextClass() {
		clsNum++;
		pos = 0;
		code = null;
		return clsNum >= classes.size();
	}

	@Override
	public int progress() {
		return clsNum;
	}
}
