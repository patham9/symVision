/**
 * Copyright 2019 The SymVision authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ptrman.levels.retina;

import ptrman.misc.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** identify M features
 *
 */
public class ProcessM {
    public static class LineParsing {
        public LineParsing(List<SingleLineDetector> lineParsing) {
            this.lineParsing = lineParsing;
        }
        
        public final List<SingleLineDetector> lineParsing;
        
        public float processGInterestRating = 0.0f; // rating or interest of the parsing for processG to be a candidate of a curve
        public boolean processGRated = false; // used to check for invalidated curves and rerate them if necessary
    }

    public Random rng = new Random();

    public List<LineParsing> lineParsings = new ArrayList<>();

    public void process(List<RetinaPrimitive> lineDetectors) {
        if( lineDetectors.isEmpty() ) {
            return;
        }
        
        tryToFindLines(lineDetectors, 1);
    }
    
    public List<LineParsing> getLineParsings()
    {
        return lineParsings;
    }
    
    private void tryToFindLines(List<RetinaPrimitive> lineDetectors, int numberOfIterations) {
        int iteration;
        
        lineParsings.clear();
        
        for( iteration = 0; iteration < numberOfIterations; iteration++ ) {
            resetMarkingsWithLocking(lineDetectors);
            selectRandomLineAndTryToTraceAndStoreItAwayWithLocking(lineDetectors);
        }
    }
    
    private static void resetMarkingsWithLocking(Iterable<RetinaPrimitive> lineDetectors) {
        // TODO< lock >
        resetMarkingsSynchronous(lineDetectors);
        // TODO< unlock >
    }
    
    private static void resetMarkingsSynchronous(Iterable<RetinaPrimitive> lineDetectors) {
        for( RetinaPrimitive iterationDetector : lineDetectors ) {
            iterationDetector.line.marked = false;
        }
    }

    private void selectRandomLineAndTryToTraceAndStoreItAwayWithLocking(List<RetinaPrimitive> lineDetectors) {

        // TODO< lock >
        
        Assert.Assert(!lineDetectors.isEmpty(), "");

        int startLineIndex = rng.nextInt(lineDetectors.size());
        SingleLineDetector startLineDetector = lineDetectors.get(startLineIndex).line;

        ArrayList<SingleLineDetector> lineParsing = findLineParsingForStartLine(startLineDetector);
        lineParsings.add(new LineParsing(lineParsing));
        
        // TODO< unlock >
    }
    
    /**
     * 
     * \result returns the (possible rng) line parsing
     * 
     * --- the lines are locked
     */
    private ArrayList<SingleLineDetector> findLineParsingForStartLine(SingleLineDetector startLineDetector) {

        ArrayList<SingleLineDetector> resultLineParsing = new ArrayList<>();
        SingleLineDetector currentLineDetector = startLineDetector;
        
        for(;;) {

            List<Intersection> remainingIntersections = deepCopyIntersections(currentLineDetector.intersections);
            
            // choose from the remaining intersections one and check it if it leads to a nonmarked edge
            for(;;) {

                if( remainingIntersections.isEmpty() ) {
                    // if we don't have any edges we can't go to any other edge/line, so the "search" is terminated
                    
                    return resultLineParsing;
                }
                
                // take out
                int indexOfChosenRemainingIntersections = rng.nextInt(remainingIntersections.size());
                Intersection currentIntersection = remainingIntersections.get(indexOfChosenRemainingIntersections);
                remainingIntersections.remove(indexOfChosenRemainingIntersections);
                
                Assert.Assert(currentIntersection.p0.primitive.type == RetinaPrimitive.EnumType.LINESEGMENT, "is not line");
                Assert.Assert(currentIntersection.p1.primitive.type == RetinaPrimitive.EnumType.LINESEGMENT, "is not line");
                
                // check out if the other side was already marked, if so, continue search for a unmarked edge/line
                if( currentIntersection.p0.primitive.line.equals(currentLineDetector) ) {
                    if( currentIntersection.p1.primitive.line.marked ) {
                        continue;
                    }
                    // else we are here
                    
                    currentIntersection.p1.primitive.line.marked = true;
                    resultLineParsing.add(currentIntersection.p1.primitive.line);
                    currentLineDetector = currentIntersection.p1.primitive.line;
                }
                else {
                    if( currentIntersection.p0.primitive.line.marked ) {
                        continue;
                    }
                    // else we are here
                    
                    currentIntersection.p0.primitive.line.marked = true;
                    resultLineParsing.add(currentIntersection.p0.primitive.line);
                    currentLineDetector = currentIntersection.p0.primitive.line;
                }
            }
        }
    }
    
    private static List<Intersection> deepCopyIntersections(List<Intersection> intersections) {
        return new ArrayList<>(intersections);
    }
}
