package ptrman.Gui;

import javax.swing.*;
import java.awt.*;

public class Interactive extends JPanel
{
    public Interactive()
    {
        super();

        

        setLayout(new GridLayout(1,2));
        
        leftCanvas=new GCanvas();
        leftCanvas.setSize(new Dimension(200, 200));
        add(leftCanvas);
        
        rightCanvas = new GCanvas();
        rightCanvas.setSize(new Dimension(200, 200));
        add(rightCanvas);
        
        
        
        setVisible(true);
        
    }
    
    public GCanvas leftCanvas;
    public GCanvas rightCanvas;
    
    
    public class GCanvas extends Canvas
    {
        public GCanvas()
        {
        }
        
        public void paint(Graphics g)
        {
            if( image == null )
            {
                return;
            }
            
            g.drawImage(image, 0, 0, image.getWidth(null), image.getHeight(null), null);
            
            g.setColor(Color.BLUE);
            g.drawLine(0, 0, 200, 200);
            
            g.dispose();
        }
        
        public void setImage(Image image)
        {
            this.image = image;
            repaint();
        }
        
        private Image image;
    }
}

