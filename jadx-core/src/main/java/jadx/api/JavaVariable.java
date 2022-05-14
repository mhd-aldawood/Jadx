package jadx.api;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import jadx.api.metadata.annotations.VarNode;

public class JavaVariable implements JavaNode {
	private final JavaMethod mth;
	private final VarNode varNode;

	public JavaVariable(JavaMethod mth, VarNode varNode) {
		this.mth = mth;
		this.varNode = varNode;
	}

	public JavaMethod getMth() {
		return mth;
	}

	public int getReg() {
		return varNode.getReg();
	}

	public int getSsa() {
		return varNode.getSsa();
	}

	@Override
	public String getName() {
		return varNode.getName();
	}

	@ApiStatus.Internal
	public VarNode getVarNode() {
		return varNode;
	}

	@Override
	public String getFullName() {
		return varNode.getType() + " " + varNode.getName() + " (r" + varNode.getReg() + "v" + varNode.getSsa() + ")";
	}

	@Override
	public JavaClass getDeclaringClass() {
		return mth.getDeclaringClass();
	}

	@Override
	public JavaClass getTopParentClass() {
		return mth.getTopParentClass();
	}

	@Override
	public int getDefPos() {
		return 0;
	}

	@Override
	public List<JavaNode> getUseIn() {
		return Collections.singletonList(mth);
	}

	@Override
	public int hashCode() {
		return varNode.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JavaVariable)) {
			return false;
		}
		return varNode.equals(((JavaVariable) o).varNode);
	}
}
