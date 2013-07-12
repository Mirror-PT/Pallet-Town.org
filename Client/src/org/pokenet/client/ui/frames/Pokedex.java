package org.pokenet.client.ui.frames;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import mdes.slick.sui.Button;
import mdes.slick.sui.Dimension;
import mdes.slick.sui.Frame;
import mdes.slick.sui.Label;
import mdes.slick.sui.TextArea;
import mdes.slick.sui.event.ActionEvent;
import mdes.slick.sui.event.ActionListener;

import org.ini4j.Ini;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;
import org.pokenet.client.GameClient;
import org.pokenet.client.backend.FileLoader;
import org.pokenet.client.backend.Translator;

import sun.security.util.Length;

/**
 * Instructions for new players
 * @author Deathlord17
 *
 */
public class Pokedex extends Frame{
    
	
	private TextArea newsTitle, newsText;
	private int iCounter;
	private String allText;

	
	private JList listbox;
	private JScrollPane scrollPane;
	public String[] listData, pokedexEntry;
	
    /**
     * Default constructor
     */
	public Pokedex(){
            initGUI();
    }
	
	/**
	 * Initialises the interface
	 */
    private void initGUI() {
    	JFrame frame = null;
    	frame = new JFrame("Pallet-Town.org Pokedex");
    	frame.setSize(300,500);
    	
		Ini ini = null;
		try {
			ini = new Ini(new FileInputStream("./res/pokemon/pokedex.ini"));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		pokedexEntry = new String[494];
		listData = new String[494];
		for (int i = 0; i < 150; i++) {
			Ini.Section s = ini.get(String.valueOf(i+1));
			String name = s.get("InternalName");
			String Pokedex = s.get("Pokedex");
			listData[i] = name;
			pokedexEntry[i] = Pokedex;
			
		}
    	listbox = new JList(listData);
    	listbox.setSize(100, 500);
    	
    	
    	
    	scrollPane = new JScrollPane();
    	scrollPane.getViewport().add(listbox);
    	
        listbox.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
            	
              JList list = (JList) evt.getSource();
              if (evt.getClickCount() == 2) { // Double-click
                //int index = list.locationToIndex(evt.getPoint());
               	JFrame pokeFrame = null;
               	JLabel label = new JLabel();
				pokeFrame = new JFrame("Pokedex - " + listData[listbox.getSelectedIndex()]);
				pokeFrame.setSize(250,  750);
				pokeFrame.setLayout(null);
				//start loading pokemon related info
				int pkmnNum = listbox.getSelectedIndex() + 1;
				System.out.println(pkmnNum);
				try { 
					if(pkmnNum < 10)
					{			
						label.setIcon(new ImageIcon("./res/pokemon/front/normal/00" + pkmnNum + "-2.png"));
					} else	if(pkmnNum < 100) {
						label.setIcon(new ImageIcon("./res/pokemon/front/normal/0" + pkmnNum + "-2.png"));
					} else  if(pkmnNum > 99) {
						label.setIcon(new ImageIcon("./res/pokemon/front/normal/" + pkmnNum + "-2.png"));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				label.setBounds(85, 5, 160, 160);
				
				
				JTextArea pkmnPokedexEntry = new JTextArea(pokedexEntry[listbox.getSelectedIndex()]);
				pkmnPokedexEntry.setWrapStyleWord(true);
				pkmnPokedexEntry.setEditable(false);
				pkmnPokedexEntry.setLineWrap(true);
				pkmnPokedexEntry.setBounds(5, 180, 230, 100);
				
				JTextArea pkmnName = new JTextArea(listData[listbox.getSelectedIndex()]);
				String tmpName = listData[listbox.getSelectedIndex()];
				Font font = new Font("Verdana", Font.BOLD, 12);		
				FontMetrics fontLength = new FontMetrics(font) {
				};
				
				pkmnName.setFont(font);
				pkmnName.setEditable(false);
				Rectangle2D c = fontLength.getStringBounds(tmpName, null);
				int v = (int) c.getWidth();
				int x = (pokeFrame.getWidth()/2) - (v/2);
				
				pkmnName.setBounds( x, 0, v, 20);
				
				//display them on the frame
				pokeFrame.add(pkmnPokedexEntry);
				pokeFrame.add(pkmnName);
				pokeFrame.add(label);
				pokeFrame.setVisible(true);
              }
            }
          });
    	
    	
    	frame.add(scrollPane);
    	
		frame.setVisible(true);
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

