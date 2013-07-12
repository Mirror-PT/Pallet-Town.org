package org.pokenet.client.ui.frames;

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
 * @author ZombieBear
 *
 */
public class HelpWindow extends Frame{
    
	private TextArea helptext,problemText, ticketText;
	private Button m_submit;
	
    /**
     * Default constructor
     */
	public HelpWindow(){
            initGUI();
    }
	
	/**
	 * Initializes the interface
	 */
    private void initGUI() {
    	getContentPane().setX(getContentPane().getX() - 1);
		getContentPane().setY(getContentPane().getY() + 1);
    	List<String> translated = Translator.translate("_GUI");
    	this.getTitleBar().getCloseButton().setVisible(true);
    	this.setTitle(translated.get(20));
    	//Window Background
    	this.setBackground(new Color(0, 0, 0, 85));
    	this.setForeground(new Color(255, 255, 255));
           
    	this.setLocation(200, 0);
    	this.setResizable(false);
           
		problemText = new TextArea();
		
		problemText.setSize(308, 126);
		problemText.setBackground(new Color(0, 0, 0, 0));
		problemText.setForeground(new Color(255, 255, 255));
		problemText.setLocation(220, 73);
		problemText.setMinimumSize(new Dimension(250,150));
		problemText.setBorderRendered(true);
		problemText.setAutoResize(false);
		problemText.setMaxChars(255);
		problemText.setVisible(true);
		this.add(problemText);
		
		m_submit = new Button("Submit Ticket");
		m_submit.setSize(96, 32);
		m_submit.setLocation(problemText.getX() + (problemText.getWidth() - m_submit.getWidth()), problemText.getY() + problemText.getHeight() + 8);
		m_submit.setVisible(true);
		m_submit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(problemText.getText() != null & !problemText.getText().equalsIgnoreCase("")) {
					submit();
				}
			}
		});
		this.add(m_submit);
		
    	ticketText = new TextArea();
    	ticketText.setSize(308, 50);
    	ticketText.setLocation(problemText.getX(), problemText.getY() - 72);
    	ticketText.setText("If you are experiencing an in-game issue and would like to speak to a Pallet-Town.org staff member, then please use the form below to submit a ticket.");
    	ticketText.setForeground(new Color(255, 255, 255));
    	ticketText.setBackground(new Color(0, 0, 0, 0));
    	ticketText.setBorderRendered(false);
    	ticketText.setEditable(false);
    	ticketText.setWrapEnabled(true);
  
    	this.add(ticketText);
		
    	helptext = new TextArea();
    	helptext.setSize(246, 264);
    	//setText Mover stuff to help panel.
    	helptext.setText(translated.get(21) +
    			translated.get(22) +
    			translated.get(23) +
    			translated.get(24) +
    			translated.get(25) +
    			translated.get(26));
//    	helptext.setFont(GlobalGame.getDPFontSmall());
    	helptext.setForeground(new Color(255, 255, 255));
    	helptext.setBackground(new Color(0, 0, 0, 0));
    	helptext.setBorderRendered(false);
    	helptext.setEditable(false);
    	helptext.setWrapEnabled(true);
    	this.setSize(534, (m_submit.getY() + 64));
    	this.add(helptext);
    	this.setBorderRendered(false);
    	setDraggable(true);
    }
       
	/**
	 * Sends login information to packet generator to be sent to server
	 */
	private void submit() {
		m_submit.setEnabled(false);
		GameClient.getInstance().getPacketGenerator().writeTcpMessage("Zmr"+problemText.getText());
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

