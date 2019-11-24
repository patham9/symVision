package cg4j.node.math;

import cg4j.Eval;
import cg4j.Tensor;
import cg4j.exception.IllegalShapeException;
import cg4j.node.Node;
import cg4j.node.io.VariableNode;

import java.util.*;

public class MultiplicationNode extends Node {


	public MultiplicationNode(String name, Node... children) {
		super(children[0].shape, name, children);
		for (int i = 1; i < children.length; i++)
			if (!Arrays.equals(new int[]{1}, children[i].shape))
				if (!Node.ShapeEndCompatible(children[0].shape, 0, children[i].shape, 0))
					throw new IllegalShapeException("Cannot multiply shapes", children[0].shape, children[1].shape);
	}

	public MultiplicationNode(Node... children) {
		super(children[0].shape, null, children);

		for (int i = 1; i < children.length; i++) {
			if (!Arrays.equals(new int[]{1}, children[i].shape))
				if (!Node.ShapeEndCompatible(children[0].shape, 0, children[i].shape, 0))
					throw new IllegalShapeException("Cannot multiply shapes", children[0].shape, children[1].shape);
		}
	}

	@Override
	protected String getNodeClassName() {
		return "MultiplicationNode";
	}

	/**
	 * Use {@code Eval#evaluate(Node)}
	 *
	 * @see Eval#evaluate(Node)
	 */
	@Override
	public Tensor evaluate(Eval e) {
		if (children.length == 1) {
			return children[0].eval(e);
		}
		Tensor out = null;

		for (Node child : children) {
			Tensor in = child.eval(e);
			int I = in.length;

			boolean init = out == null;
			if (init) out = new Tensor(new float[I], in.shape);

			for (int i = 0; i < out.length; i++) {
				int ii = i % I;
				out.set(i, init ? in.get(ii) : out.get(i) * in.get(ii));
			}

		}
		return out;
	}

	@Override
	public void createGradients(Map<VariableNode, Node> deltas, Node parentDelta) {
		List<Node> multToAdd = new ArrayList<>(children.length - 1);
		for (Node child : children) {
			for (Node childJ : children)
				if (child != childJ)
					multToAdd.add(childJ);
			multToAdd.add(parentDelta);
			child.createGradients(deltas, new MultiplicationNode(multToAdd.toArray(EmptyNodeArray)));
			multToAdd.clear();
		}
	}
}