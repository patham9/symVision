/**
 * Copyright 2019 The SymVision authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ptrman.bpsolver;

import ptrman.Datastructures.IMap2d;
import ptrman.Datastructures.Vector2d;
import ptrman.FargGeneral.network.Network;
import ptrman.FargGeneral.network.Node;
import ptrman.bpsolver.ltm.LinkCreator;
import ptrman.bpsolver.nodes.PlatonicPrimitiveNode;
import ptrman.levels.retina.*;
import ptrman.levels.retina.helper.ProcessConnector;

import java.util.*;

public class Solver {
    public float processdmaximalDistanceOfPositions = 30.0f;// was 6.0f;


    public List<ProcessA.Sample> debugSamples;

    public Solver() {
    }

    public void setup() {
        initializeNetwork();
        setupLtmFactoryDefault();
        initializePlatonicPrimitiveDatabase();

        createConnectors();
        setupProcesses();
    }

    private void createConnectors() {
        // mode is PRIMARY_QUEUE because the java version of processB uses the workspace, and the opengl version uses the queue
        connectorSamplesFromProcessA = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.PRIMARY_QUEUE);
        allConnectors.add(connectorSamplesFromProcessA);

        connectorSamplesFromProcessB = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorSamplesFromProcessB);

        connectorSamplesFromProcessC = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.PRIMARY_QUEUE);
        allConnectors.add(connectorSamplesFromProcessC);
        connectorSamplesFromProcessCToProcessF = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.QUEUE);
        allConnectors.add(connectorSamplesFromProcessCToProcessF);

        connectorSamplesForEndosceleton = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorSamplesForEndosceleton);
        connectorDetectorsEndosceletonFromProcessD = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorDetectorsEndosceletonFromProcessD);
        connectorDetectorsExosceletonFromProcessD = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorDetectorsExosceletonFromProcessD);

        connectorSamplesFromProcessF = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorSamplesFromProcessF);

        connectorDetectorsEndosceletonFromProcessH = ProcessConnector.createWithDefaultQueues(ProcessConnector.EnumMode.WORKSPACE);
        allConnectors.add(connectorSamplesFromProcessF);
    }

    private void setupProcesses() {
        processA = new ProcessA();
        processB = new ProcessB();
        processC = new ProcessC();
        endosceletonProcessD = new ProcessD();
        exosceletonProcessD = new ProcessD();
        processF = new ProcessF();
        processH = new ProcessH();

        endosceletonSampleFilter = new ProcessSampleFilter(ProcessA.Sample.EnumType.ENDOSCELETON);

        processA.setImageSize(getImageSize());
        processA.setup();

        processB.setImageSize(getImageSize());
        processB.setup();

        final int PROCESSCGRIDSIZE = 8;
        processC.setImageSize(getImageSize());
        processC.gridsize = PROCESSCGRIDSIZE;
        processC.setup();

        endosceletonProcessD.setImageSize(getImageSize());
        endosceletonProcessD.set(connectorSamplesForEndosceleton, connectorDetectorsEndosceletonFromProcessD);

        endosceletonProcessD.setup();

        exosceletonProcessD.setImageSize(getImageSize());
        exosceletonProcessD.set(connectorSamplesFromProcessF, connectorDetectorsExosceletonFromProcessD);
        exosceletonProcessD.setup();

        processF.setImageSize(getImageSize());
        processF.preSetup(connectorSamplesFromProcessCToProcessF, connectorSamplesFromProcessF);
        processF.setup();

        processH.setImageSize(getImageSize());
        processH.set(connectorDetectorsEndosceletonFromProcessD, connectorDetectorsEndosceletonFromProcessH);
        processH.setup();


        // TODO< others >

        // setup filters
        endosceletonSampleFilter.preSetupSet(connectorSamplesFromProcessC, connectorSamplesForEndosceleton);
        endosceletonSampleFilter.setup();

    }

    public void cycle(int cycleCount) {

    }

    public void recalculate(IMap2d<Boolean> image) {
        recalculate(image, 1.0f);
    }

    /** use throttle < 1.0 to degrade quality */
    public void recalculate(IMap2d<Boolean> image, float throttle) {
        final boolean enableProcessH = true;
        final boolean enableProcessE = true;
        final boolean enableProcessM = false;

        final boolean debugLineIntersections = false;

        final int NUMBEROFCYCLES = 500;


//        Queue<ProcessA.Sample> queueToProcessF = new ArrayDeque<>();


        /*
        ProcessD endosceletonProcessD = new ProcessD();
        ProcessD exosceletonProcessD = new ProcessD();
        ProcessH endosceletonProcessH = new ProcessH();
        ProcessH exosceletonProcessH = new ProcessH();
        ProcessE processE = new ProcessE();
        ProcessM processM = new ProcessM();
        ProcessF processF = new ProcessF();
        */

        allConnectors.forEach(ProcessConnector::flush);


        ProcessZFacade processZFacade = new ProcessZFacade();

        final int processzNumberOfPixelsToMagnifyThreshold = 8;

        final int processZGridsize = 8;

        processZFacade.setImageSize(getImageSize());
        processZFacade.preSetupSet(processZGridsize, processzNumberOfPixelsToMagnifyThreshold);
        processZFacade.setup();

        processZFacade.set(image); // image doesn't need to be copied

        processZFacade.preProcessData();
        processZFacade.processData();
        processZFacade.postProcessData();

        notMagnifiedOutputObjectIdsMapDebug = processZFacade.getNotMagnifiedOutputObjectIds();

        // copy image because processA changes the image
        processA.set(image.copy(), processZFacade.getNotMagnifiedOutputObjectIds(), connectorSamplesFromProcessA);

        processB.set(image.copy(), connectorSamplesFromProcessA, connectorSamplesFromProcessB);

        processC.set(connectorSamplesFromProcessB, connectorSamplesFromProcessC, connectorSamplesFromProcessCToProcessF);

        processF.set(image);


        // preProcessData
        processA.preProcessData();
        processB.preProcessData();
        processC.preProcessData();
        processF.preProcessData();

        endosceletonSampleFilter.preProcessData();


        endosceletonProcessD.preProcessData();
        //exosceletonProcessD.preProcessData();

        processH.preProcessData();



        // processData
        processA.processData(throttle);

        debugSamples = connectorSamplesFromProcessA.getOut();

        processB.processData();
        processC.processData(throttle);
        endosceletonSampleFilter.processData();


        processF.processData();

        endosceletonProcessD.processData(throttle);
        //exosceletonProcessD.processData();

        processH.processData();



        // postProcessData
        processA.postProcessData();
        processB.postProcessData();
        processC.postProcessData();
        endosceletonSampleFilter.postProcessData();
        processF.postProcessData();

        endosceletonProcessD.postProcessData();
        //exosceletonProcessD.postProcessData();

        processH.postProcessData();



        System.out.println("connectorDetectorsEndosceletonFromProcessD " + connectorDetectorsEndosceletonFromProcessD.inSize());
        System.out.println("connectorDetectorsExosceletonFromProcessD " + connectorDetectorsExosceletonFromProcessD.inSize());
        /*

        // copy because processA changes the image
        processA.setImageSize(getImageSize());
        processA.set(image.copy(), processZFacade.getNotMagnifiedOutputObjectIds(), connectorSamplesFromProcessA);
        List<ProcessA.Sample> endosceletonSamples = processA.sampleImage();


        ProcessSampleFilter endosceletonSampleFilter = new ProcessSampleFilter(ProcessA.Sample.EnumType.ENDOSCELETON);

        Queue<ProcessA.Sample> sampleQueueFromProcessC = new ArrayDeque<>();
        Queue<ProcessA.Sample> sampleQueueForEndosceleton = new ArrayDeque<>();

        Queue<ProcessA.Sample> processFOutputSampleQueue = new ArrayDeque<>();
        Queue<ProcessA.Sample> toExosceletonProcessDSampleQueue = new ArrayDeque<>();

        Queue<RetinaPrimitive> queueLineDetectorEndosceletonFromProcessD = new ArrayDeque<>(); // new naming style
        Queue<RetinaPrimitive> queueLineDetectorExosceletonFromProcessD = new ArrayDeque<>(); // new naming style

        Queue<RetinaPrimitive> queueLineDetectorEndosceletonFromProcessH = new ArrayDeque<>(); // new naming style
        Queue<RetinaPrimitive> queueLineDetectorExosceletonFromProcessH = new ArrayDeque<>(); // new naming style

        endosceletonProcessH.set(queueLineDetectorEndosceletonFromProcessD, queueLineDetectorEndosceletonFromProcessH);
        endosceletonProcessH.setup();

        exosceletonProcessH.set(queueLineDetectorExosceletonFromProcessD, queueLineDetectorExosceletonFromProcessH);
        exosceletonProcessH.setup();

        processB.setImageSize(getImageSize());

        processB.process(endosceletonSamples, image);

        try {
            Thread.sleep(500000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<ProcessA.Sample> samplesWithAltitude = new ArrayList<>();

        for( final ProcessA.Sample iterationSample : endosceletonSamples ) {
            samplesWithAltitude.add(iterationSample);
        }

        System.out.println("Samples with altitude size " + Integer.toString(samplesWithAltitude.size()));





        endosceletonSampleFilter.preSetupSet(sampleQueueFromProcessC, sampleQueueForEndosceleton);
        endosceletonSampleFilter.setup();




        processC.setImageSize(getImageSize());
        processC.preSetupSet(8 gridsize, sampleQueueFromProcessC);
        processC.setup();


        processC.set(endosceletonSamples);
        processC.recalculate();


        processC.processData();


        endosceletonSampleFilter.processData();



        processF.setImageSize(getImageSize());
        processF.preSetup(queueToProcessF, processFOutputSampleQueue);
        processF.setup();

        processF.set(image);
        processF.processData();

        System.out.println("processFOutputSampleQueue size " + Integer.toString(processFOutputSampleQueue.size()));



        endosceletonProcessD.setImageSize(getImageSize());
        endosceletonProcessD.set(sampleQueueForEndosceleton, queueLineDetectorEndosceletonFromProcessD);

        endosceletonProcessD.preSetupSet(6.0f    #maximalDistanceOfPositions#);
        endosceletonProcessD.setup();
        endosceletonProcessD.processData();
        //List<RetinaPrimitive> lineDetectors = endosceletonProcessD.getResultRetinaPrimitives();



        // take out the samples from a queue, put it into a list and a output queue
        // TODO< put this into a own process >

        List<ProcessA.Sample> exosceletonSamples = new ArrayList<>();
        int remainingSize = processFOutputSampleQueue.size();
        for( int i = 0; i < remainingSize; i++ ) {
            final ProcessA.Sample currentSample = processFOutputSampleQueue.poll();
            exosceletonSamples.add(currentSample);
            toExosceletonProcessDSampleQueue.add(currentSample);
        }



        exosceletonProcessD.setImageSize(getImageSize());
        exosceletonProcessD.set(toExosceletonProcessDSampleQueue, queueLineDetectorExosceletonFromProcessD);

        exosceletonProcessD.preSetupSet(6.0f #maximalDistanceOfPositions#);
        exosceletonProcessD.setup();
        exosceletonProcessD.processData();




        List<Intersection> lineIntersections = new ArrayList<>();



        if( enableProcessH ) {
            endosceletonProcessH.processData();
            exosceletonProcessH.processData();
        }


        *###

        if( enableProcessE ) {
            processE.process(lineDetectors, image);

            if( debugLineIntersections ) {
                lineIntersections = getAllLineIntersections(lineDetectors);
            }
        }

        List<ProcessM.LineParsing> lineParsings = new ArrayList<>();

        if( enableProcessM ) {
            processM.process(lineDetectors);

            lineParsings = processM.getLineParsings();
        }

        *###

        List<RetinaPrimitive> lineDetectorsEndosceletonAfterProcessH = getAllElementsFromQueueAsList(queueLineDetectorEndosceletonFromProcessH);

        System.out.println("lineDetectorsEndosceletonAfterProcessH size " + Integer.toString(lineDetectorsEndosceletonAfterProcessH.size()));


        ITranslatorStrategy retinaToWorkspaceTranslatorStrategy = new IdStrategy();

        List<Node> objectNodes = retinaToWorkspaceTranslatorStrategy.createObjectsFromRetinaPrimitives(lineDetectorsEndosceletonAfterProcessH, this);

        cycle(NUMBEROFCYCLES);



        if( false ) {
            FeaturePatternMatching featurePatternMatching;

            featurePatternMatching = new FeaturePatternMatching();

            for( Node iterationNode : objectNodes ) {
                Node bestPatternNode;
                float bestPatternSimilarity;

                bestPatternNode = null;
                bestPatternSimilarity = 0.0f;

                for( Node patternNode : patternRootNodes ) {
                    List<FeaturePatternMatching.MatchingPathElement> matchingPathElements;
                    float matchingDistanceValue;
                    float matchingSimilarityValue;

                    matchingPathElements = featurePatternMatching.matchAnyRecursive(iterationNode, patternNode, networkHandles, Arrays.asList(Link.EnumType.CONTAINS), HardParameters.PatternMatching.MAXDEPTH);
                    matchingDistanceValue = FeaturePatternMatching.calculateRatingWithDefaultStrategy(matchingPathElements);
                    matchingSimilarityValue = FeaturePatternMatching.Converter.distanceToSimilarity(matchingDistanceValue);

                    if( matchingSimilarityValue > Parameters.getPatternMatchingMinSimilarity() && matchingSimilarityValue > bestPatternSimilarity ) {
                        bestPatternNode = patternNode;
                        bestPatternSimilarity = matchingSimilarityValue;
                    }
                }

                if( bestPatternNode != null ) {
                    // TODO< incorperate new pattern into old >
                    int debugPoint = 0;
                }
                else {
                    patternRootNodes.add(iterationNode);
                }
            }
        }

        lastFrameObjectNodes = objectNodes;
        lastFrameRetinaPrimitives = lineDetectorsEndosceletonAfterProcessH; // for now only the line detectors TODO
                                                                            // for now only that whats written here
        lastFrameEndosceletonSamples = endosceletonSamples;
        lastFrameExosceletonSamples = exosceletonSamples;
        lastFrameSamplesWithAltitude = samplesWithAltitude;
        lastFrameIntersections = lineIntersections; // TODO< other intersections too >

        */
    }
    
    /**
     * 
     * stores all factory preset nodes in the ltm (standard node types, linked attributes, etc)
     */
    public void setupLtmFactoryDefault() {


        networkHandles.objectPlatonicPrimitiveNode = new PlatonicPrimitiveNode("Object", null);
        networkHandles.lineStructureAbstractPrimitiveNode = new PlatonicPrimitiveNode("lineStructure", null);
        networkHandles.lineSegmentPlatonicPrimitiveNode = new PlatonicPrimitiveNode("LineSegment", null);
        
        networkHandles.bayPlatonicPrimitiveNode = new PlatonicPrimitiveNode("bay", null /* TODO "Bay" */);
        networkHandles.endpointPlatonicPrimitiveNode = new PlatonicPrimitiveNode("endpoint", "EndPoint");
        networkHandles.barycenterPlatonicPrimitiveNode = new PlatonicPrimitiveNode("barycenter", "BaryCenter");
        networkHandles.lineSegmentFeatureLineLengthPrimitiveNode = new PlatonicPrimitiveNode("LineSegmentLength", "LineSegmentLength");
        networkHandles.lineSegmentFeatureLineSlopePrimitiveNode = new PlatonicPrimitiveNode("LineSegmentSlope", "LineSegmentSlope");
        
        networkHandles.xCoordinatePlatonicPrimitiveNode = new PlatonicPrimitiveNode("xCoordinate", null);
        networkHandles.yCoordinatePlatonicPrimitiveNode = new PlatonicPrimitiveNode("yCoordinate", null);
        
        // currently not connected to anything
        networkHandles.anglePointNodePlatonicPrimitiveNode = new PlatonicPrimitiveNode("AnglePoint", null);
        
        networkHandles.anglePointFeatureTypePrimitiveNode = new PlatonicPrimitiveNode("AnglePointFeatureType", null);
        networkHandles.anglePointPositionPlatonicPrimitiveNode = new PlatonicPrimitiveNode("AnglePointPosition", null);
        networkHandles.anglePointAngleValuePrimitiveNode = new PlatonicPrimitiveNode("AnglePointAngleValue", "Angle");
        
        networkHandles.lineStructureAbstractPrimitiveNode.isAbstract = true;
        ptrman.FargGeneral.network.Link link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.endpointPlatonicPrimitiveNode);
        networkHandles.lineStructureAbstractPrimitiveNode.out(link);
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.bayPlatonicPrimitiveNode);
        networkHandles.lineStructureAbstractPrimitiveNode.out(link);
        
        
        network.nodes.add(networkHandles.lineSegmentPlatonicPrimitiveNode);
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.ISA, networkHandles.lineStructureAbstractPrimitiveNode);
        networkHandles.lineSegmentPlatonicPrimitiveNode.out(link);
        
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.lineSegmentFeatureLineLengthPrimitiveNode);
        networkHandles.lineSegmentPlatonicPrimitiveNode.out(link);
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.lineSegmentFeatureLineSlopePrimitiveNode);
        networkHandles.lineSegmentPlatonicPrimitiveNode.out(link);
        
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.ISA, networkHandles.objectPlatonicPrimitiveNode);
        networkHandles.lineStructureAbstractPrimitiveNode.out(link);
        
        // TODO< imagination of circle, center, tangent lines, etc >
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.xCoordinatePlatonicPrimitiveNode);
        networkHandles.endpointPlatonicPrimitiveNode.out(link);
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.yCoordinatePlatonicPrimitiveNode);
        networkHandles.endpointPlatonicPrimitiveNode.out(link);
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.xCoordinatePlatonicPrimitiveNode);
        networkHandles.bayPlatonicPrimitiveNode.out(link);
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.yCoordinatePlatonicPrimitiveNode);
        networkHandles.bayPlatonicPrimitiveNode.out(link);
        
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.xCoordinatePlatonicPrimitiveNode);
        networkHandles.barycenterPlatonicPrimitiveNode.out(link);
        
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.yCoordinatePlatonicPrimitiveNode);
        networkHandles.barycenterPlatonicPrimitiveNode.out(link);
        
        
        
        // a object has a barycenter
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.barycenterPlatonicPrimitiveNode);
        networkHandles.objectPlatonicPrimitiveNode.out(link);
        
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASATTRIBUTE, networkHandles.anglePointFeatureTypePrimitiveNode);
        networkHandles.anglePointNodePlatonicPrimitiveNode.out(link);
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.anglePointPositionPlatonicPrimitiveNode);
        networkHandles.anglePointNodePlatonicPrimitiveNode.out(link);
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.anglePointAngleValuePrimitiveNode);
        networkHandles.anglePointNodePlatonicPrimitiveNode.out(link);
        
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.xCoordinatePlatonicPrimitiveNode);
        networkHandles.anglePointPositionPlatonicPrimitiveNode.out(link);
        link = network.linkCreator.createLink(ptrman.FargGeneral.network.Link.EnumType.HASFEATURE, networkHandles.yCoordinatePlatonicPrimitiveNode);
        networkHandles.anglePointPositionPlatonicPrimitiveNode.out(link);
    }
    
    private void initializeNetwork() {
        network.linkCreator = new LinkCreator();
    }

    private void initializePlatonicPrimitiveDatabase() {
        platonicPrimitiveDatabase.calculatorsForMaxValueOfPlatonicPrimitiveNode.put(networkHandles.xCoordinatePlatonicPrimitiveNode, new PlatonicPrimitiveDatabase.ConstantValueMaxValueCalculator(getImageSizeAsFloat().x));
        platonicPrimitiveDatabase.calculatorsForMaxValueOfPlatonicPrimitiveNode.put(networkHandles.yCoordinatePlatonicPrimitiveNode, new PlatonicPrimitiveDatabase.ConstantValueMaxValueCalculator(getImageSizeAsFloat().y));
        platonicPrimitiveDatabase.calculatorsForMaxValueOfPlatonicPrimitiveNode.put(networkHandles.lineSegmentFeatureLineLengthPrimitiveNode, new PlatonicPrimitiveDatabase.ConstantValueMaxValueCalculator((float)Math.sqrt(getImageSizeAsFloat().x*getImageSizeAsFloat().x + getImageSizeAsFloat().y*getImageSizeAsFloat().y)));
        platonicPrimitiveDatabase.calculatorsForMaxValueOfPlatonicPrimitiveNode.put(networkHandles.lineSegmentFeatureLineSlopePrimitiveNode, new PlatonicPrimitiveDatabase.ConstantValueMaxValueCalculator(getImageSizeAsFloat().y));
        platonicPrimitiveDatabase.calculatorsForMaxValueOfPlatonicPrimitiveNode.put(networkHandles.anglePointAngleValuePrimitiveNode, new PlatonicPrimitiveDatabase.ConstantValueMaxValueCalculator(360.0f));
    }



    // TODO< refactor out >
    private static List<Intersection> getAllLineIntersections(Iterable<RetinaPrimitive> lineDetectors) {

        List<Intersection> uniqueIntersections = new ArrayList<>();

        for( RetinaPrimitive currentPrimitive : lineDetectors ) {
            if( currentPrimitive.type != RetinaPrimitive.EnumType.LINESEGMENT ) {
                continue;
            }

            findAndAddUniqueIntersections(uniqueIntersections, currentPrimitive.line.intersections);
        }

        return uniqueIntersections;
    }

    // modifies uniqueIntersections
    private static void findAndAddUniqueIntersections(Collection<Intersection> uniqueIntersections, Iterable<Intersection> intersections) {
        for( Intersection currentOuterIntersection : intersections ) {

            boolean found = false;

            for( Intersection currentUnqiueIntersection : uniqueIntersections ) {
                if( currentUnqiueIntersection.equals(currentOuterIntersection) ) {
                    found = true;
                    break;
                }
            }

            if( !found ) {
                uniqueIntersections.add(currentOuterIntersection);
            }
        }


    }







    // both ltm and workspace
    // the difference is that the nodes of the workspace may all be deleted
    public final Network network = new Network();
    public final NetworkHandles networkHandles = new NetworkHandles();
    public final PlatonicPrimitiveDatabase platonicPrimitiveDatabase = new PlatonicPrimitiveDatabase();

    // all stored patterns
    public List<Node> patternRootNodes = new ArrayList<>();

    public Vector2d<Float> getImageSizeAsFloat()
    {
        return Vector2d.ConverterHelper.convertIntVectorToFloat(imageSize);
    }
    
    public Vector2d<Integer> getImageSize()
    {
        return imageSize;
    }
    
    public void setImageSize(Vector2d<Integer> imageSize)
    {
        this.imageSize = imageSize;
    }
    
    private Vector2d<Integer> imageSize; 
    
    public List<Node> lastFrameObjectNodes;
    public List<RetinaPrimitive> lastFrameRetinaPrimitives;
    public List<ProcessA.Sample> lastFrameEndosceletonSamples;
    public List<ProcessA.Sample> lastFrameExosceletonSamples;
    public List<ProcessA.Sample> lastFrameSamplesWithAltitude;
    public List<Intersection> lastFrameIntersections;

    public IMap2d<Integer> notMagnifiedOutputObjectIdsMapDebug;


    private ProcessA processA;
    private AbstractProcessB processB;
    private ProcessC processC;
    private ProcessD endosceletonProcessD;
    private ProcessD exosceletonProcessD;
    private ProcessF processF;
    private ProcessH processH;

    private ProcessSampleFilter endosceletonSampleFilter;

    private ProcessConnector<ProcessA.Sample> connectorSamplesFromProcessA;
    private ProcessConnector connectorSamplesFromProcessB;
    private ProcessConnector<ProcessA.Sample> connectorSamplesFromProcessC;
    private ProcessConnector<ProcessA.Sample> connectorSamplesFromProcessCToProcessF;
    private ProcessConnector<ProcessA.Sample> connectorSamplesForEndosceleton;
    public ProcessConnector<RetinaPrimitive> connectorDetectorsEndosceletonFromProcessD;
    private ProcessConnector<RetinaPrimitive> connectorDetectorsExosceletonFromProcessD;
    private ProcessConnector<ProcessA.Sample> connectorSamplesFromProcessF;
    private ProcessConnector<RetinaPrimitive> connectorDetectorsEndosceletonFromProcessH;

    private final Collection<ProcessConnector> allConnectors = new ArrayList<>();
}
