/**
 * Copyright 2019 The SymVision authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ptrman.bpsolver.RetinaToWorkspaceTranslator;

import org.apache.commons.math3.linear.ArrayRealVector;
import ptrman.Datastructures.SpatialAcceleration;
import ptrman.FargGeneral.network.Link;
import ptrman.FargGeneral.network.Node;
import ptrman.bpsolver.Solver;
import ptrman.bpsolver.nodes.PlatonicPrimitiveInstanceNode;
import ptrman.levels.retina.Intersection;
import ptrman.levels.retina.RetinaPrimitive;
import ptrman.misc.Assert;

import java.util.*;

/**
 * implements a strategy which groups retina objects based on the intersections of retina objects
 * 
 */
public class NearIntersectionStrategy extends AbstractTranslatorStrategy {


    @Override
    public List<Node> createObjectsFromRetinaPrimitives(List<RetinaPrimitive> primitives, Solver bpSolver) {
        List<RetinaObjectWithAssociatedPointsAndWorkspaceNode> retinaObjectsWithAssociatedPoints;

        // TODO< hard parameters >
        final int GRIDCOUNTX = 10;
        final int GRIDCOUNTY = 10;

        SpatialAccelerationForCrosspointsWithMappingOfRetinaObjects spatialAccelerationForCrosspointsWithMappingOfRetinaObjects = new SpatialAccelerationForCrosspointsWithMappingOfRetinaObjects();
        spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.spatialForCrosspoints = new SpatialAcceleration<>(GRIDCOUNTX, GRIDCOUNTY, bpSolver.getImageSize().x, bpSolver.getImageSize().y);

        spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap = NearIntersectionStrategy.createWorkspaceNodeAndRegisterCodeletsAndOutputAsMapFromRetinaPrimitives(primitives, bpSolver);

        bundleAllIntersections(spatialAccelerationForCrosspointsWithMappingOfRetinaObjects, primitives);
        calculateAnglePointType(spatialAccelerationForCrosspointsWithMappingOfRetinaObjects);
        createLinksAndNodesForAnglePoints(spatialAccelerationForCrosspointsWithMappingOfRetinaObjects, bpSolver);
        
        //retinaObjectsWithAssociatedPoints = convertPrimitivesToRetinaObjectsWithAssoc(primitives);
        //storeRetinaObjectWithAssocIntoMap(retinaObjectsWithAssociatedPoints, spatialAccelerationForCrosspointsWithMappingOfRetinaObjects);
        
        
        // NOTE< does hashmap work? >
        Map<RetinaPrimitive, Boolean> remainingRetinaObjects = new HashMap<>();

        NearIntersectionStrategy.resetAllMarkings(primitives);
        
        // algorithm
        
        // ...
        storeAllRetinaPrimitivesInMap(primitives, remainingRetinaObjects);
        
        // then pick one at random and put connected (assert unmarked) retinaObjects into the same object and put them out of the map
        // repeat this until no remaining retina object is in the map
        
        return NearIntersectionStrategy.pickRetinaPrimitiveAtRandomUntilNoCandidateIsLeftAndReturnItAsObjects(remainingRetinaObjects, spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap, random, bpSolver);
    }

    private static Map<RetinaPrimitive,RetinaObjectWithAssociatedPointsAndWorkspaceNode> createWorkspaceNodeAndRegisterCodeletsAndOutputAsMapFromRetinaPrimitives(Iterable<RetinaPrimitive> primitives, Solver bpSolver) {

        Map<RetinaPrimitive, RetinaObjectWithAssociatedPointsAndWorkspaceNode> resultMap = new HashMap<>();

        for( RetinaPrimitive iterationRetinaPrimitive : primitives ) {

            PlatonicPrimitiveInstanceNode createdPlatonicInstanceNodeForRetinaObject = createPlatonicInstanceNodeForRetinaObject(iterationRetinaPrimitive, bpSolver.networkHandles);

            RetinaObjectWithAssociatedPointsAndWorkspaceNode retinaObjectWithAssocPointsAndWorkspace = new RetinaObjectWithAssociatedPointsAndWorkspaceNode(iterationRetinaPrimitive);
            retinaObjectWithAssocPointsAndWorkspace.workspaceNode = createdPlatonicInstanceNodeForRetinaObject;

            resultMap.put(iterationRetinaPrimitive, retinaObjectWithAssocPointsAndWorkspace);
        }

        return resultMap;
    }

    private static List<RetinaObjectWithAssociatedPointsAndWorkspaceNode> convertPrimitivesToRetinaObjectsWithAssoc(final Iterable<RetinaPrimitive> primitives) {
        List<RetinaObjectWithAssociatedPointsAndWorkspaceNode> retinaObjectsWithAssociatedPoints = new ArrayList<>();
        
        for( RetinaPrimitive iterationPrimitive : primitives ) {
            retinaObjectsWithAssociatedPoints.add(associatePointsToRetinaPrimitive(iterationPrimitive));
        }
        
        return retinaObjectsWithAssociatedPoints;
    }
    
    private static void storeAllRetinaPrimitivesInMap(Iterable<RetinaPrimitive> primitives, Map<RetinaPrimitive, Boolean> map) {
        for( RetinaPrimitive iterationPrimitive : primitives ) {
            map.put(iterationPrimitive, Boolean.FALSE);
        }
    }
    
    private static List<RetinaPrimitive> pickRetinaPrimitiveAtRandomAndMarkAndRemoveConnectedPrimitivesAndRetunListOfPrimitives(Map<RetinaPrimitive, Boolean> map, Random random) {

        List<RetinaPrimitive> resultPrimitives = new ArrayList<>();
        List<RetinaPrimitive> openList = new ArrayList<>();
        
        {

            RetinaPrimitive chosenStartRetinaPrimitive = pickRandomRetinaPrimitiveFromMap(map, random);
            openList.add(chosenStartRetinaPrimitive);
        }
        
        for(;;) {

            int os = openList.size();

            if( os == 0 )
                break;


            RetinaPrimitive retinaPrimitiveFromOpenList = openList.get(os - 1);
            openList.remove(os -1);
            
            // we do this because we are "done" with it
            map.remove(retinaPrimitiveFromOpenList);
            // mark it for the same reason
            retinaPrimitiveFromOpenList.marked = true;
            
            resultPrimitives.add(retinaPrimitiveFromOpenList);

            List<RetinaPrimitive> notYetMarkedConnectedRetinaPrimitives = getNotYetMarkedConnectedRetinaPrimitives(retinaPrimitiveFromOpenList);
            
            openList.addAll(notYetMarkedConnectedRetinaPrimitives);
        }
        
        return resultPrimitives;
    }
    
    private static RetinaPrimitive pickRandomRetinaPrimitiveFromMap(Map<RetinaPrimitive, Boolean> map, Random random) {

        Object[] array = map.keySet().toArray();

        int index = random.nextInt(array.length);
        return (RetinaPrimitive)array[index];
    }

    private static List<RetinaPrimitive> getNotYetMarkedConnectedRetinaPrimitives(RetinaPrimitive retinaPrimitiveFromOpenList) {

        Collection<RetinaPrimitive> unfilteredIntersectionPartners = new ArrayList<>();

        List<Intersection> allIntersections = retinaPrimitiveFromOpenList.getIntersections();
        
        for( Intersection iterationIntersection : allIntersections ) {
            unfilteredIntersectionPartners.add(iterationIntersection.getOtherPartner(retinaPrimitiveFromOpenList).primitive);
        }

        List<RetinaPrimitive> filteredIntersectionPartners = filterRetinaPrimitivesForUnmarkedOnes(unfilteredIntersectionPartners);
        return filteredIntersectionPartners;
    }
    
    private static List<RetinaPrimitive> filterRetinaPrimitivesForUnmarkedOnes(Iterable<RetinaPrimitive> unfilteredIntersectionPartners) {

        List<RetinaPrimitive> result = new ArrayList<>();
        
        for( RetinaPrimitive iterationRetinaPrimitive : unfilteredIntersectionPartners ) {
            if( !iterationRetinaPrimitive.marked ) {
                result.add(iterationRetinaPrimitive);
            }
        }
        
        return result;
    }
    

    private static List<Node> pickRetinaPrimitiveAtRandomUntilNoCandidateIsLeftAndReturnItAsObjects(Map<RetinaPrimitive, Boolean> map, Map<RetinaPrimitive,RetinaObjectWithAssociatedPointsAndWorkspaceNode> primitveToRetinaObjectWithAssocMap, Random random, Solver bpSolver) {
        List<Node> resultObjectNodes = new ArrayList<>();
        
        for(;;) {

            if( map.size() == 0 ) {
                return resultObjectNodes;
            }

            List<RetinaPrimitive> retinaPrimitivesOfObject = pickRetinaPrimitiveAtRandomAndMarkAndRemoveConnectedPrimitivesAndRetunListOfPrimitives(map, random);

            PlatonicPrimitiveInstanceNode objectNode = new PlatonicPrimitiveInstanceNode(bpSolver.networkHandles.objectPlatonicPrimitiveNode);
            
            // create for the retinaPrimitives network nodes and link them
            {
                for( RetinaPrimitive iterationPrimitive : retinaPrimitivesOfObject ) {

                    Node nodeForRetinaPrimitive = primitveToRetinaObjectWithAssocMap.get(iterationPrimitive).workspaceNode;

                    // linkage
                    //      forward
                    objectNode.out(bpSolver.network.linkCreator.createLink(Link.EnumType.CONTAINS, nodeForRetinaPrimitive));
                    //      reverse
                    nodeForRetinaPrimitive.out(bpSolver.network.linkCreator.createLink(Link.EnumType.ISPARTOF, objectNode));
                }
            }
            
            resultObjectNodes.add(objectNode);
        }
    }
    

    private static void resetAllMarkings(Iterable<RetinaPrimitive> primitives) {
        for( RetinaPrimitive iterationPrimitive : primitives ) {
            iterationPrimitive.marked = false;
        }
    }
    
    private void bundleAllIntersections(SpatialAccelerationForCrosspointsWithMappingOfRetinaObjects spatialAccelerationForCrosspointsWithMappingOfRetinaObjects, Iterable<RetinaPrimitive> listOfRetinaPrimitives) {
        for( RetinaPrimitive iterationRetinaPrimitive : listOfRetinaPrimitives ) {

            // we store the intersectionposition and the intersectionpartners

            for( Intersection iterationIntersection : iterationRetinaPrimitive.getIntersections()) {
                List<SpatialAcceleration<Crosspoint>.Element> crosspointsAtPosition = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.spatialForCrosspoints.getElementsNearPoint(iterationIntersection.intersectionPosition, 1000.0f /* TODO const */);
                
                if( crosspointsAtPosition.isEmpty() ) {

                    SpatialAcceleration<Crosspoint>.Element createdCrosspointElement = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.spatialForCrosspoints.new Element();
                    createdCrosspointElement.data = new Crosspoint();
                    createdCrosspointElement.data.position = iterationIntersection.intersectionPosition;
                    createdCrosspointElement.position = iterationIntersection.intersectionPosition;


                    RetinaObjectWithAssociatedPointsAndWorkspaceNode retinaObjectWithAssoc = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap.get(iterationIntersection.p0.primitive);
                    Assert.Assert(retinaObjectWithAssoc != null, "");
                    createdCrosspointElement.data.adjacentRetinaObjects.add(new Crosspoint.RetinaObjectWithAssocWithIntersectionType(retinaObjectWithAssoc, iterationIntersection.p0.intersectionEndpointType));
                    
                    retinaObjectWithAssoc = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap.get(iterationIntersection.p1.primitive);
                    Assert.Assert(retinaObjectWithAssoc != null, "");
                    createdCrosspointElement.data.adjacentRetinaObjects.add(new Crosspoint.RetinaObjectWithAssocWithIntersectionType(retinaObjectWithAssoc, iterationIntersection.p1.intersectionEndpointType));
                    
                    spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.spatialForCrosspoints.addElement(createdCrosspointElement);
                }
                else {

                    SpatialAcceleration<Crosspoint>.Element nearestCrosspointElement = getNearestCrosspointElement(crosspointsAtPosition, iterationIntersection.intersectionPosition);

                    RetinaObjectWithAssociatedPointsAndWorkspaceNode retinaObjectWithAssoc = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap.get(iterationIntersection.p0.primitive);
                    Assert.Assert(retinaObjectWithAssoc != null, "");

                    if( !nearestCrosspointElement.data.doesAdjacentRetinaObjectsContain(retinaObjectWithAssoc) ) {
                        nearestCrosspointElement.data.adjacentRetinaObjects.add(new Crosspoint.RetinaObjectWithAssocWithIntersectionType(retinaObjectWithAssoc, iterationIntersection.p0.intersectionEndpointType));
                    }
                    
                    retinaObjectWithAssoc = spatialAccelerationForCrosspointsWithMappingOfRetinaObjects.primitiveToRetinaObjectWithAssocMap.get(iterationIntersection.p1.primitive);
                    Assert.Assert(retinaObjectWithAssoc != null, "");
                    
                    if( !nearestCrosspointElement.data.doesAdjacentRetinaObjectsContain(retinaObjectWithAssoc) ) {
                        nearestCrosspointElement.data.adjacentRetinaObjects.add(new Crosspoint.RetinaObjectWithAssocWithIntersectionType(retinaObjectWithAssoc, iterationIntersection.p1.intersectionEndpointType));
                    }
                    
                }
            }
        }
    }
    
    private static SpatialAcceleration<Crosspoint>.Element getNearestCrosspointElement(final List<SpatialAcceleration<Crosspoint>.Element> crosspointElements, final ArrayRealVector position) {
        SpatialAcceleration<Crosspoint>.Element nearestElement = crosspointElements.get(0);
        double nearestDistance = crosspointElements.get(0).position.getDistance(position);

        for( SpatialAcceleration<Crosspoint>.Element iterationCrosspointElement : crosspointElements ) {
            final double distance = iterationCrosspointElement.position.getDistance(position);
            if( distance < nearestDistance ) {
                nearestDistance = distance;
                nearestElement = iterationCrosspointElement;
            }
        }
        
        return nearestElement;
    }
    
    private final Random random = new Random();
}
