package ptrman.bpsolver.codelets;

import ptrman.Datastructures.Vector2d;
import ptrman.FargGeneral.network.Link;
import ptrman.bpsolver.BpSolver;
import ptrman.bpsolver.HelperFunctions;
import ptrman.bpsolver.RetinaToWorkspaceTranslator.PointProximityStrategy;
import ptrman.bpsolver.SolverCodelet;
import ptrman.bpsolver.nodes.AttributeNode;
import ptrman.bpsolver.nodes.FeatureNode;
import ptrman.bpsolver.nodes.NodeTypes;
import ptrman.bpsolver.nodes.PlatonicPrimitiveInstanceNode;
import ptrman.math.TruncatedFisherYades;
import ptrman.misc.AngleHelper;
import ptrman.misc.Assert;

import java.util.ArrayList;
import java.util.Random;

import static ptrman.Datastructures.Vector2d.FloatHelper.normalize;
import static ptrman.Datastructures.Vector2d.FloatHelper.sub;
import static ptrman.misc.Assert.Assert;

/**
 *
 * calculates the angle(s) of a anglePoint
 * should be be called only once!
 */
public class Angle extends SolverCodelet
{
    private static final int KPOINTNUMBEROFANGLESUNTILSTOCHASTICCHOICE = 10;
    private static final int KPOINTNUMBEROFCHOSENANGLES = 10; // must be smaller or equal to KPOINTNUMBEROFANGLESUNTILSTOCHASTICCHOICE

    private static class AngleInformation
    {
        public AngleInformation(float angle, int count)
        {
            this.angle = angle;
            this.count = count;
        }
        
        public int count; // number of the connections from the anglepoint to the (not jet created) attribute node
        public float angle;
    }
    
    public Angle(BpSolver bpSolver)
    {
        super(bpSolver);
    }

    @Override
    public void initialize()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SolverCodelet cloneObject()
    {
        Angle cloned;
        
        cloned = new Angle(bpSolver);
        return cloned;
    }

    @Override
    public RunResult run()
    {
        PointProximityStrategy.Crosspoint.EnumAnglePointType anglePointType;
        ArrayList<PlatonicPrimitiveInstanceNode> anglePartners;
        Vector2d<Float> anglePosition;
        ArrayList<AngleInformation> angleInformations;
        ArrayList<Float> angles;
        
        angleInformations = new ArrayList<>();
        
        Assert(startNode.type == NodeTypes.EnumType.PLATONICPRIMITIVEINSTANCENODE.ordinal() && ((PlatonicPrimitiveInstanceNode)startNode).primitiveNode.equals(getNetworkHandles().anglePointNodePlatonicPrimitiveNode), "");
        
        anglePointType = getAnglePointType((PlatonicPrimitiveInstanceNode)startNode);
        anglePartners = getPartnersOfAnglepoint((PlatonicPrimitiveInstanceNode)startNode);
        anglePosition = getAnglePosition((PlatonicPrimitiveInstanceNode)startNode);
        
        // checks
        if( anglePointType == PointProximityStrategy.Crosspoint.EnumAnglePointType.K )
        {
            Assert.Assert(anglePartners.size() >= 3, "");
        }
        else if( anglePointType == PointProximityStrategy.Crosspoint.EnumAnglePointType.V )
        {
            Assert.Assert(anglePartners.size() == 2, "");
        }
        else if( anglePointType == PointProximityStrategy.Crosspoint.EnumAnglePointType.X )
        {
            Assert.Assert(anglePartners.size() >= 2 && anglePartners.size() <= 4, "");
        }
        else if( anglePointType == PointProximityStrategy.Crosspoint.EnumAnglePointType.T )
        {
            Assert.Assert(anglePartners.size() == 2 || anglePartners.size() == 3, "");
        }
        else
        {
            // relates to BUG 0001
            // we just return when this case triggers
            return new RunResult(false);
            
            // uncomment when the bug is fixed
            //throw new InternalError();
        }
        
        
        angles = calculateAnglesBetweenPartners(anglePointType == PointProximityStrategy.Crosspoint.EnumAnglePointType.K ? EnumIsKPoint.YES : EnumIsKPoint.NO,
            anglePartners,
            anglePosition
        );
        angleInformations = bundleAnglesAndCreateAngleInformations(angles);
        
        createNodesAndLinkAngleInformation((PlatonicPrimitiveInstanceNode)startNode, angleInformations);
        createAndLinkAnglePointType((PlatonicPrimitiveInstanceNode)startNode, anglePointType);
        
        return new RunResult(false);
    }
    
    private void createNodesAndLinkAngleInformation(PlatonicPrimitiveInstanceNode anglePointPrimitiveInstanceNode, ArrayList<AngleInformation> angleInformations)
    {
        for( AngleInformation iterationAngle : angleInformations )
        {
            FeatureNode createdFeatureNode;
            int linkI;
            
            createdFeatureNode = FeatureNode.createFloatNode(getNetworkHandles().anglePointAngleValuePrimitiveNode, iterationAngle.angle, 1, bpSolver.platonicPrimitiveDatabase.getMaxValueByPrimitiveNode(getNetworkHandles().anglePointAngleValuePrimitiveNode));
            
            for( linkI = 0; linkI < iterationAngle.count; linkI++ )
            {
                Link createdLink;
                
                createdLink = getNetwork().linkCreator.createLink(Link.EnumType.HASFEATURE, createdFeatureNode);
                
                anglePointPrimitiveInstanceNode.outgoingLinks.add(createdLink);
            }
        }
    }
    
    private void createAndLinkAnglePointType(PlatonicPrimitiveInstanceNode anglePointPrimitiveInstanceNode, PointProximityStrategy.Crosspoint.EnumAnglePointType anglePointType)
    {
        AttributeNode createAnglePointTypeNode;
        Link createdLink;
        
        createAnglePointTypeNode = AttributeNode.createIntegerNode(getNetworkHandles().anglePointFeatureTypePrimitiveNode, anglePointType.ordinal());
        
        createdLink = getNetwork().linkCreator.createLink(Link.EnumType.HASATTRIBUTE, createAnglePointTypeNode);
        anglePointPrimitiveInstanceNode.outgoingLinks.add(createdLink);
    }
    
    private PointProximityStrategy.Crosspoint.EnumAnglePointType getAnglePointType(final PlatonicPrimitiveInstanceNode anglePointNode)
    {
        for( Link iterationLink : anglePointNode.getLinksByType(Link.EnumType.HASATTRIBUTE) )
        {
            AttributeNode targetAttributeNode;
            
            if( iterationLink.target.type != NodeTypes.EnumType.ATTRIBUTENODE.ordinal() )
            {
                continue;
            }
            
            targetAttributeNode = (AttributeNode)iterationLink.target;
            
            if( !targetAttributeNode.attributeTypeNode.equals(getNetworkHandles().anglePointFeatureTypePrimitiveNode) )
            {
                continue;
            }
            
            return PointProximityStrategy.Crosspoint.EnumAnglePointType.fromInteger(targetAttributeNode.getValueAsInt());
            
        }
        
        throw new InternalError();
    }
    
    private Vector2d<Float> getAnglePosition(PlatonicPrimitiveInstanceNode platonicPrimitiveInstanceNode)
    {
        for( Link iterationLink : platonicPrimitiveInstanceNode.getLinksByType(Link.EnumType.HASATTRIBUTE) )
        {
            PlatonicPrimitiveInstanceNode targetNode;
            
            if( iterationLink.target.type != NodeTypes.EnumType.PLATONICPRIMITIVEINSTANCENODE.ordinal() )
            {
                continue;
            }
            
            targetNode = (PlatonicPrimitiveInstanceNode)iterationLink.target;
            
            if( !targetNode.primitiveNode.equals(getNetworkHandles().anglePointPositionPlatonicPrimitiveNode) )
            {
                continue;
            }
            
            return HelperFunctions.getVectorFromVectorAttributeNode(getNetworkHandles(), targetNode);
        }
        
        throw new InternalError();
    }
    
    private ArrayList<PlatonicPrimitiveInstanceNode> getPartnersOfAnglepoint(final PlatonicPrimitiveInstanceNode anglePointNode)
    {
        ArrayList<PlatonicPrimitiveInstanceNode> resultList;
        
        resultList = new ArrayList<>();
        
        for( Link iterationLink : anglePointNode.getLinksByType(Link.EnumType.ISPARTOF) )
        {
            Assert.Assert(iterationLink.target.type == NodeTypes.EnumType.PLATONICPRIMITIVEINSTANCENODE.ordinal(), "");
            resultList.add((PlatonicPrimitiveInstanceNode)iterationLink.target);
        }
        
        Assert.Assert(resultList.size() > 0, "");
        return resultList;
    }
    
    private float measureAngleBetweenPartnersAtPosition(PlatonicPrimitiveInstanceNode a, PlatonicPrimitiveInstanceNode b, Vector2d<Float> position)
    {
        Vector2d<Float> tangentA, tangentB;
        
        tangentA = getTangentOfPlatonicPrimitiveInstanceAtPosition(a, position);
        tangentB = getTangentOfPlatonicPrimitiveInstanceAtPosition(b, position);
        
        return AngleHelper.getMinimalAngleInDegreeBetweenNormalizedVectors(tangentA, tangentB);
    }
    
    
    private Vector2d<Float> getTangentOfPlatonicPrimitiveInstanceAtPosition(PlatonicPrimitiveInstanceNode platonicPrimitive, Vector2d<Float> position)
    {
        if( platonicPrimitive.primitiveNode.equals(getNetworkHandles().lineSegmentPlatonicPrimitiveNode) )
        {
            Vector2d<Float> diff;
            
            diff = sub(platonicPrimitive.p1, platonicPrimitive.p2);
            return normalize(diff);
        }
        else
        {
            throw new InternalError("Unexpected type of primitive!");
        }
    }
    
    
    private ArrayList<AngleInformation> bundleAnglesAndCreateAngleInformations(ArrayList<Float> angles)
    {
        ArrayList<AngleInformation> resultAngleInformation;
        
        resultAngleInformation = new ArrayList<>();
        
        for( float angle : angles )
        {
            boolean similarAngleWasFound;
            
            similarAngleWasFound = false;
            
            for( AngleInformation iterationAngleInformation : resultAngleInformation )
            {
                if( Math.abs(angle - iterationAngleInformation.angle) < ANGLEMAXDIFFERENCE )
                {
                    iterationAngleInformation.count++;
                    similarAngleWasFound = true;
                    break;
                }
            }
            
            if( !similarAngleWasFound )
            {
                AngleInformation createdAngleInformation;
                
                createdAngleInformation = new AngleInformation(angle, 1);
                resultAngleInformation.add(createdAngleInformation);
            }
        }
        
        return resultAngleInformation;
    }

    private ArrayList<Float> calculateAnglesBetweenPartners(EnumIsKPoint isKpoint, ArrayList<PlatonicPrimitiveInstanceNode> anglePartners, Vector2d<Float> anglePosition)
    {
        ArrayList<Float> angleResult;
        
        angleResult = new ArrayList<>();
        
        int numberOfCombinations;
        
        numberOfCombinations = ptrman.math.Math.faculty(anglePartners.size());
            
        if( isKpoint == EnumIsKPoint.YES && numberOfCombinations > KPOINTNUMBEROFANGLESUNTILSTOCHASTICCHOICE )
        {
            int i;
            
            // NOTE PERFORMANCE< the Fisher yades algorithm is maybe too slow, future will tell >
            // NOTE< selection policy could be better, we measure only one angle per partner, could be many >
            
            TruncatedFisherYades truncatedFisherYades;
            
            truncatedFisherYades = new TruncatedFisherYades((anglePartners.size()*anglePartners.size() - anglePartners.size()) / 2, new GeneratorImplementation());
            
            for( i = 0; i < KPOINTNUMBEROFANGLESUNTILSTOCHASTICCHOICE; i++)
            {
                Tuple<Integer> indices;
                int partnerIndexA, partnerIndexB;
                
                indices = (Tuple<Integer>)truncatedFisherYades.takeOne(random);
                partnerIndexA = indices.left;
                partnerIndexB = indices.right;
                
                Assert.Assert(partnerIndexA >= 0 && partnerIndexA < anglePartners.size(), "Invalid index");
                Assert.Assert(partnerIndexB >= 0 && partnerIndexB < anglePartners.size(), "Invalid index");
                
                angleResult.add(measureAngleBetweenPartnersAtPosition(anglePartners.get(partnerIndexA), anglePartners.get(partnerIndexB), anglePosition));
            }
        }
        else
        {
            int lower, higher;
            
            for( lower = 0; lower < anglePartners.size(); lower++ )
            {
                for( higher = lower+1; higher < anglePartners.size(); higher++ )
                {
                    angleResult.add(measureAngleBetweenPartnersAtPosition(anglePartners.get(0), anglePartners.get(1), anglePosition));
                }
            }
        }
        
        return angleResult;
    }
    
    private static class GeneratorImplementation implements TruncatedFisherYades.IGenerator<Tuple<Integer>>
    {
        public GeneratorImplementation()
        {
        }
        
        @Override
        public Tuple<Integer> generate(int index)
        {
            Tuple<Integer> triangleIndices;
            
            triangleIndices = getIndicesOfTriangle(index);
            
            // y+1 because we take the lower triangle in the matrix of the index combinations
            return new Tuple<>(triangleIndices.left, triangleIndices.right+1);
        }
        
        // calculate the index inside a triangle strip
        // examples
        
        // 0
        // result 0
        
        // 0
        // xx
        // result 0xx
        
        // 0
        // xx
        // 000
        // result 0xx000
        
        // ...
        private Tuple<Integer> getIndicesOfTriangle(int index)
        {
            int width;
            int remainingIndex;
            int yIndex;
            
            yIndex = 0;
            width = 1;
            remainingIndex = index;
            
            for(;;)
            {
                if( remainingIndex < width )
                {
                    return new Tuple<>(remainingIndex, yIndex);
                }
                
                remainingIndex -= width;
                yIndex++;
                width++;
            }
        }
        
    }

    private static class Tuple<T>
    {
        public T left;
        public T right;
        
        public Tuple(T left, T right)
        {
            this.left = left;
            this.right = right;
        }
    }
    
    private enum EnumIsKPoint
    {
        NO,
        YES
    }
    
    private Random random = new Random();
    
    private static float ANGLEMAXDIFFERENCE = 5.0f;
}