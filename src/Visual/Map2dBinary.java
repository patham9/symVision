package Visual;

import Datastructures.IMap2d;
import Datastructures.Map2d;


public class Map2dBinary
{
    public static IMap2d<Boolean> negate(IMap2d<Boolean> input)
    {
        Map2d<Boolean> result;
        int x, y;

        result = new Map2d<Boolean>(input.getWidth(), input.getLength());

        for (y = 0; y < input.getLength(); y++)
        {
            for (x = 0; x < input.getWidth(); x++)
            {
                result.setAt(x, y, !input.readAt(x, y));
            }
        }

        return result;
    }

    public static IMap2d<Boolean> corode(IMap2d<Boolean> input)
    {
        IMap2d<Boolean> result;
        int x, y;

        result = new Map2d<>(input.getWidth(), input.getLength());

        for( y = 1; y < input.getLength() - 1; y++ )
        {
            for( x = 1; x < input.getWidth() - 1; x++ )
            {
                // optimized

                if(
                        !input.readAt(x-1,y-1) ||
                                !input.readAt(x,y-1) ||
                                !input.readAt(x+1,y-1) ||

                                !input.readAt(x-1,y) ||
                                !input.readAt(x,y) ||
                                !input.readAt(x+1,y) ||

                                !input.readAt(x-1,y+1) ||
                                !input.readAt(x,y+1) ||
                                !input.readAt(x+1,y+1)
                        )
                {
                    continue;
                }

                result.setAt(x, y, true);
            }
        }

        return result;
    }
}