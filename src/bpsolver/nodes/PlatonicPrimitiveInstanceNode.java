package bpsolver.nodes;

import Datastructures.Vector2d;
import FargGeneral.network.Node;

/**
 * is a instance of a platonic primitive, for example a line or a curve
 * 
 */
public class PlatonicPrimitiveInstanceNode extends Node
{
    public PlatonicPrimitiveInstanceNode(PlatonicPrimitiveNode primitiveNode)
    {
        super(NodeTypes.EnumType.PLATONICPRIMITIVEINSTANCENODE.ordinal());
        
        this.primitiveNode = primitiveNode;
    }
    
    public PlatonicPrimitiveNode primitiveNode;
    
    // used to store data
    // linesegment : uses p1 and p2
    // point       : uses p1
    public Vector2d<Float> p1;
    public Vector2d<Float> p2;
}