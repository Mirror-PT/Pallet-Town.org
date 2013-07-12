package org.pokenet.client.ui.frames;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import mdes.slick.sui.Button;
import mdes.slick.sui.Dimension;
import mdes.slick.sui.Frame;
import mdes.slick.sui.TextArea;
import mdes.slick.sui.event.ActionEvent;
import mdes.slick.sui.event.ActionListener;

import org.newdawn.slick.Color;
import org.pokenet.client.GameClient;
import org.pokenet.client.backend.Translator;

/**
 * Instructions for new players
 * @author Deathlord17
 *
 */
public class newsWindow extends Frame{
    
	
	private TextArea newsTitle, newsText;
	private int iCounter;
	private String allText;

	
    /**
     * Default constructor
     */
	public newsWindow(){
            initGUI();
    }
	
	/**
	 * Initializes the interface
	 */
    private void initGUI() {
    	getContentPane().setX(getContentPane().getX() - 1);
		getContentPane().setY(getContentPane().getY() + 1);
    	this.getTitleBar().getCloseButton().setVisible(true);
    	this.setTitle("Pallet-Town.org News");
    	//Window Background
    	this.setBackground(new Color(0, 0, 0, 85));
    	this.setForeground(new Color(255, 255, 255));
           
    	this.setLocation(200, 0);
    	this.setResizable(false);
    	
           
    	
    	newsTitle = new TextArea();
    	newsTitle.setSize(400, 30);
    	newsTitle.setBackground(new Color(0, 0, 0, 0));
    	newsTitle.setForeground(new Color(255, 255, 255));
    	newsTitle.setEditable(false);
    	newsTitle.setWrapEnabled(true);
    	
    	newsText = new TextArea();
    	newsText.setSize(400, 400);
    	newsText.setForeground(new Color(255, 255, 255));
    	newsText.setBackground(new Color(0, 0, 0, 0));
    	newsText.setEditable(false);
    	newsText.setWrapEnabled(true);
    	newsText.setBorderRendered(false);
    	
    	try {
    		URL url = new URL("http://pallet-town.org/news.txt");
    		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
    		String str;
    		String[] lines;
    		lines = new String[999];
    		
    		while ((str = in.readLine()) != null) {
    			iCounter++;
    			if(str != null)
    				lines[iCounter] = str;
    			System.out.println(str);
    			
    		}
    		int iCount = 1;
			while (iCount <= iCounter) {
    			if(lines[iCounter] == "")
    				continue;
    			if(iCount == 1) {
    				allText = lines[iCount] + "\n";
    			} else {
    				allText = allText + lines[iCount] + "\n";
    			}
    			
    			iCount += 1;
    		}
			
    		newsText.setText(allText);
    		
    	} catch (MalformedURLException e) {
    		System.out.println("Failed to load news");
    	} catch (IOException e) {
    		System.out.println("Failed to load news");
    	}
    	this.setSize(400, 500);
    	this.add(newsText);
    	setDraggable(true);
    }
       
	
    /**
     * Sets the size
     */
    @Override
    public void setSize(float width, float height) {
            super.setSize(width, height);
            //if (helptext != null) helptext.setSize(width -5, height -5);
    }
   
    /**
     * Sets the width
     */
    @Override
    public void setWidth(float width) {
            super.setWidth(width);
            //if (helptext != null) helptext.setWidth(width -5);
    }
   
    /**
     * Sets the height
     */
    @Override
    public void setHeight(float height) {
            super.setHeight(height);
            //if (helptext != null) helptext.setHeight(height -5);
    }
}

