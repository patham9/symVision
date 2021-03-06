/**
 * Copyright 2019 The SymVision authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ptrman.levels.visual;

import ptrman.Datastructures.*;
import ptrman.meter.event.DurationStartMeter;
import ptrman.misc.Assert;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 *
 */
public class VisualProcessor
{
    public static class ThresholdMap2dMapperFunction implements Function<Float, Boolean> {
        public ThresholdMap2dMapperFunction(float threshold) {
            this.threshold = threshold;
        }

        @Override
        public Boolean apply(Float value)
        {
            return value > threshold;
        }

        private final float threshold;
    }

    public static class ConvertToGrayImageMap2dMapperFunction implements Function<ColorRgb, Float> {
        private final ColorRgb colorScale;

        public ConvertToGrayImageMap2dMapperFunction(ColorRgb colorScale)
        {
            this.colorScale = colorScale;
        }

        @Override
        public Float apply(ColorRgb value)
        {
            return value.getScaledNormalizedMagnitude(colorScale);
        }
    }

//    public static class FunctionMapperFunction implements java.util.function.Function<Float, Float> {
//        public interface IFunction {
//            float calculate(float value);
//        }
//
//        @Override
//        public Float apply(Float value) {
//            return function.calculate(value);
//        }
//
//        public FunctionMapperFunction(IFunction function) {
//            this.function = function;
//        }
//
//        private final IFunction function;
//
//    }

    public static class ProcessingChain
    {
        public enum EnumMapType {
            COLOR,
            BOOLEAN,
            FLOAT
        }

        public static class MarrHildrethOperatorParameter
        {
            public MarrHildrethOperatorParameter(int filterSize, float sigma)
            {
                this.filterSize = filterSize;
                this.sigma = sigma;
            }

            public final int filterSize;
            public final float sigma;
        }

        public interface IFilter<TypeInput, TypeOutput> {
            void apply(IMap2d<TypeInput> input, IMap2d<TypeOutput> output);
        }

        public static class DitheringFilter implements IFilter<Float, Boolean> {
            @Override
            public void apply(IMap2d<Float> input, IMap2d<Boolean> output) {
                Map2dDither.Generic.floydSteinbergDitheringFloatToBoolean(input, output);
            }
        }

        public static class ThresholdFilter implements IFilter<Float, Boolean> {
            public ThresholdFilter(float threshold) {
                this.threshold = threshold;
            }

            @Override
            public void apply(IMap2d<Float> input, IMap2d<Boolean> output) {
                Map2dMapper.map(new ThresholdMap2dMapperFunction(threshold), input, output);
            }

            private final float threshold;

        }

        public static class ConvertColorRgbToGrayscaleFilter implements IFilter<ColorRgb, Float> {
            public ConvertColorRgbToGrayscaleFilter(ColorRgb colorToGrayColorScale) {
                this.colorToGrayColorScale = colorToGrayColorScale;
            }

            @Override
            public void apply(IMap2d<ColorRgb> input, IMap2d<Float> output) {
                Map2dMapper.map(new ConvertToGrayImageMap2dMapperFunction(colorToGrayColorScale), input, output);
            }

            private final ColorRgb colorToGrayColorScale;
        }



        public abstract static class ChainElement {
            public ChainElement(EnumMapType inputType, EnumMapType outputType, String meterName) {
                this.inputType = inputType;
                this.outputType = outputType;
                durationMeters = new DurationStartMeter(meterName, true, 1.0, false);
            }

            public abstract void apply();

            public final EnumMapType inputType;
            public final EnumMapType outputType;
            public final DurationStartMeter durationMeters;
        }

        public abstract static class ApplyChainElement<InputType, ResultType> extends ChainElement {
            public ApplyChainElement(EnumMapType inputType, EnumMapType outputType, String meterName, Vector2d<Integer> imageSize, IFilter<InputType, ResultType> filter) {
                super(inputType, outputType, meterName);
                this.result = newResultMap(imageSize.x, imageSize.y);
                this.filter = filter;
            }

            /** default, but can be overridden in subclasses */
            protected IMap2d<ResultType> newResultMap(int w, int h) {
                return new Map2d<>(w, h);
            }

            public void apply() {
                durationMeters.start();
                filter.apply(input, result);
                durationMeters.stop();
            }

            public IMap2d<InputType> input;
            public final IMap2d<ResultType> result;
            private final IFilter<InputType, ResultType> filter;
        }

        public static class ChainElementFloatFloat extends ApplyChainElement<Float, Float> {
            public ChainElementFloatFloat(IFilter<Float, Float> filter, String meterName, Vector2d<Integer> imageSize) {
                super(EnumMapType.FLOAT, EnumMapType.FLOAT, meterName, imageSize, filter);
            }

            @Override
            protected IMap2d<Float> newResultMap(int w, int h) {
                return new FloatMap2d(w, h);
            }

        }

        public static class ChainElementFloatBoolean extends ApplyChainElement<Float, Boolean> {
            public ChainElementFloatBoolean(IFilter<Float, Boolean> filter, String meterName, Vector2d<Integer> imageSize) {
                super(EnumMapType.FLOAT, EnumMapType.BOOLEAN, meterName, imageSize, filter);
            }

            @Override
            protected IMap2d<Boolean> newResultMap(int w, int h) {
                return new FastBooleanMap2d(w, h);
            }
        }

        public static class ChainElementColorFloat extends ApplyChainElement<ColorRgb, Float> {
            public ChainElementColorFloat(IFilter<ColorRgb, Float> filter, String meterName, Vector2d<Integer> imageSize) {
                super(EnumMapType.COLOR, EnumMapType.FLOAT, meterName, imageSize, filter);
            }
        }



        public ProcessingChain() {

        }

        public void filterChain(IMap2d<ColorRgb> inputColorImage)
        {


            Deque<Integer> chainIndicesToProcess = new ArrayDeque<>();

            boolean processFromInput = true;
            chainIndicesToProcess.add(0);

            for(;;) {

                if( chainIndicesToProcess.isEmpty() ) {
                    break;
                }

                int currentDagElementIndex = chainIndicesToProcess.pollFirst();

                Dag.Element<ChainElement> currentDagElement = filterChainDag.elements.get(currentDagElementIndex);

                if( processFromInput ) {

                    processFromInput = false;

                    Assert.Assert(currentDagElement.content.inputType == EnumMapType.COLOR, "");
                    Assert.Assert(currentDagElement.content.outputType == EnumMapType.FLOAT, "");

                    ChainElementColorFloat chainElement = (ChainElementColorFloat) currentDagElement.content;

                    chainElement.input = inputColorImage;
                }

                currentDagElement.content.apply();

                IMap2d MapForFilterOutput = ((ApplyChainElement) currentDagElement.content).result;


                currentDagElement.childIndices.forEach( iterationChildIndex -> {

                    Dag.Element<ChainElement> iterationDagElement = filterChainDag.elements.get(iterationChildIndex);

                    Assert.Assert(iterationDagElement.content.inputType == currentDagElement.content.outputType, "Types of filters are incompatible");

                    ((ApplyChainElement)iterationDagElement.content).input = MapForFilterOutput;

                    chainIndicesToProcess.add(iterationChildIndex);
                });


            }


        }

        // entry is [0]
        public final Dag<ChainElement> filterChainDag = new Dag<>();

    }

}
