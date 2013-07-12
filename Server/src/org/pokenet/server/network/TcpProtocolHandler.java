package org.pokenet.server.network;



import java.io.IOException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.sql.SQLException;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.pokenet.server.GameServer;
import org.pokenet.server.backend.ItemProcessor;
import org.pokenet.server.backend.entity.Bag;
import org.pokenet.server.backend.entity.PlayerChar;
import org.pokenet.server.backend.entity.PlayerChar.RequestType;
import org.pokenet.server.backend.entity.Positionable.Direction;
import org.pokenet.server.battle.BattleTurn;
import org.pokenet.server.battle.impl.PvPBattleField;
import org.pokenet.server.battle.impl.WildBattleField;
import org.pokenet.server.network.message.ItemMessage;
import org.pokenet.server.network.message.PokenetMessage;
import org.pokenet.server.network.message.RequestMessage;

/**
 * Handles packets received from players over TCP
 * @author shadowkanji
 *
 */
public class TcpProtocolHandler extends IoHandlerAdapter {
	public static HashMap<String, PlayerChar> m_players;
	private LoginManager m_loginManager;
	private LogoutManager m_logoutManager;
	private RegistrationManager m_regManager;
	
	/**
	 * Constructor
	 * @param login
	 * @param logout
	 */
	public TcpProtocolHandler(LoginManager login, LogoutManager logout) {
		m_loginManager = login;
		m_logoutManager = logout;
		m_regManager = new RegistrationManager();
		m_regManager.start();
	}
	
	static {
		m_players = new HashMap<String, PlayerChar>();
	}
	
	@Override
	public void sessionCreated(IoSession session) {
		//Tell the client which revision the server is on
		session.write("R" + GameServer.REVISION);
	}
	
	/**
	 * Handles any exceptions involving a player's session
	 */
	public void exceptionCaught(IoSession session, Throwable t)
	throws Exception {
		/*
		 * Attempt to disconnect and logout the player (save their data)
		 */
		try {
			PlayerChar p = (PlayerChar) session.getAttribute("player");
			if(p != null) {
				if (p.isBattling())
					p.lostBattle();
				m_logoutManager.queuePlayer(p);
				GameServer.getServiceManager().getMovementService().removePlayer(p.getName());
				MySqlManager m = new MySqlManager();
				if(m.connect(GameServer.getDatabaseHost(), 
						GameServer.getDatabaseUsername(), 
						GameServer.getDatabasePassword())) {
					m.selectDatabase(GameServer.getDatabaseName());
					m.query("UPDATE friends_data SET isOnline=0 WHERE friendName='" + p.getName() + "'");
					m.close();
				}
				m_players.remove(p);
			}
			if(!(t instanceof IOException) || session.isConnected() || !session.isClosing())
				session.close(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		t.printStackTrace();
	}
	
	/**
	    * Once the server receives a packet from the client, this method is run.
	    * @param IoSession session - A client session
	    * @param Object msg - The packet received from the client
		*/
	public void messageReceived(IoSession session, Object msg) throws Exception {
		String message = (String) msg;
		String [] details;
		String name;
		String [] AddItem;
		String itemID;
		String itemQuantity;
		MySqlManager m = new MySqlManager();
		//if (!message.equals("L") && !message.equals("R") && !message.equals("D") && !message.equals("U")) {
		//	System.out.println(message);
		//}
		if(session.getAttribute("player") == null) {
			/*
			 * The player hasn't been logged in, only allow login and registration packets
			 */
			switch(message.charAt(0)) {
			case 'l':
				//Login packet
				details = message.substring(1).split(",");
				m_loginManager.queuePlayer(session, details[0], details[1]);
				break;
			case 'r':
				//Registration packet
				m_regManager.queueRegistration(session, message.substring(1));
				break;
			case 'c':
				//Change password packet
				details = message.substring(1).split(",");
				m_loginManager.queuePasswordChange(session, details[0], details[1], details[2]);
				break;
			case 'f':
				//Force login
				details = message.substring(1).split(",");
				m_loginManager.queuePlayer(session, details[0], details[1]);
				break;
			}
		} else {
			/*
			 * Player is logged in, allow interaction with their player object
			 */
			PlayerChar p = (PlayerChar) session.getAttribute("player");
			p.lastPacket = System.currentTimeMillis();
			switch(message.charAt(0)) {
			case 'U':
				//Move up
				p.queueMovement(Direction.Up);
				break;
			case 'D':
				//Move down
				p.queueMovement(Direction.Down);
				break;
			case 'L':
				//Move left
				p.queueMovement(Direction.Left);
				break;
			case 'R':
				//Move right
				p.queueMovement(Direction.Right);
				break;
			case 'P':
				//Pokemon interaction
				int pokemonIndex = 0;
				String move;
				switch(message.charAt(1)) {
				case 'm':
					//Player is allowing move to be learned
					pokemonIndex = Integer.parseInt(String.valueOf(message.charAt(2)));
					int moveIndex = Integer.parseInt(String.valueOf(message.charAt(3)));
					move = message.substring(4);
					if(move != null && !move.equalsIgnoreCase("") &&
							p.getParty()[pokemonIndex] != null) {
						if(p.getParty()[pokemonIndex].getMovesLearning().contains(move)) {
							p.getParty()[pokemonIndex].learnMove(moveIndex, move);
							p.updateClientPP(pokemonIndex, moveIndex);
						}
					}
					break;
				case 'M':
					//Player is not allowing the move to be learned
					pokemonIndex = Integer.parseInt(String.valueOf(message.charAt(2)));
					move = message.substring(3);
					if(p.getParty()[pokemonIndex] != null) {
						if(p.getParty()[pokemonIndex].getMovesLearning().contains(move)) {
							p.getParty()[pokemonIndex].getMovesLearning().remove(move);
						}
					}
					break;
				case 'e':
					//Evolution response
					pokemonIndex = Integer.parseInt(String.valueOf(message.charAt(3)));
					if(p.getParty()[pokemonIndex] != null) {
						switch(message.charAt(2)) {
						case '0':
							//Cancel evolution
							p.getParty()[pokemonIndex].evolutionResponse(false, p);
							break;
						case '1':
							//Allow evolution
							p.getParty()[pokemonIndex].evolutionResponse(true, p);
							break;
						}
					}
					break;
				}
				break;
			case 's':
				//Party swapping
				p.swapPokemon(Integer.parseInt(message.substring(1, message.indexOf(','))), 
						Integer.parseInt(message.substring(message.indexOf(',') + 1)));
				break;
			case 'S':
				//Shop interaction
				if(p.isShopping()) {
					int item = -1;
					switch(message.charAt(1)) {
					case 'b':
						//Buy items. Sent as SbITEMID,QUANTITY
						item = Integer.parseInt(message.substring(2, message.indexOf(',')));
						//int q = Integer.parseInt(message.substring(message.indexOf(',') + 1));
						p.buyItem(item, 1);
						break;
					case 's':
						//Sell items. Sent as SsITEMID,QUANTITY
						item = Integer.parseInt(message.substring(2, message.indexOf(',')));
						//int q = Integer.parseInt(message.substring(message.indexOf(',') + 1));
						p.sellItem(item, 1);
						break;
					case 'f':
						//Finished shopping
						p.setShopping(false);
						break;
					}
				} else if(p.isSpriting()) {
					//Sprite changing
					int sprite = Integer.parseInt(message.substring(1));
					/* Ensure the user buys a visible sprite */
					if(sprite > 0 && !GameServer.getServiceManager().
							getSpriteList().getUnbuyableSprites().contains(sprite)) {
						if(p.getMoney() >= 0) {
							p.setMoney(p.getMoney() - 0);
							p.updateClientMoney();
							p.setSprite(sprite);
							p.setSpriting(false);
						}
					}
				}
				break;
			case 'r':
				String player = message.substring(2);
				//A request was sent
				switch(message.charAt(1)) {
				case 'b':
					//Battle Request rbUSERNAME
					if(m_players.containsKey(player)) {
						TcpProtocolHandler.writeMessage(m_players.get(player).getTcpSession(), 
								new RequestMessage(RequestType.BATTLE, p.getName()));
						p.addRequest(player, RequestType.BATTLE);
					}
					break;
				case 't':
					//Trade Request rtUSERNAME
					if(m_players.containsKey(player)) {
						TcpProtocolHandler.writeMessage(m_players.get(player).getTcpSession(), 
								new RequestMessage(RequestType.TRADE, p.getName()));
						p.addRequest(player, RequestType.TRADE);
					}
					break;
				case 'a':
					//Request accepted raUSERNAME
					if(m_players.containsKey(player)) {
						m_players.get(player).requestAccepted(p.getName());
					}
					break;
				case 'c':
					//Request declined rcUSERNAME
					if(m_players.containsKey(player)) {
						m_players.get(player).removeRequest(p.getName());
					}
					break;
				}
				break;
			case 'B':
				//Box interaction
				if(p.isBoxing()) {
					switch(message.charAt(1)) {
					case 'r':
						//Requesting info for box number - e.g. Br0
						int boxNum = Integer.parseInt(String.valueOf(message.charAt(2)));
						if(boxNum >= 0 && boxNum < 9)
							p.sendBoxInfo(boxNum);
						break;
					case 'R':
						//Releasing a pokemon from storage - sent as BRBOXNUM,BOXSLOT
						details = message.substring(2).split(",");
						p.releasePokemon(Integer.parseInt(details[0]), Integer.parseInt(details[1]));
						break;
					case 's':
						//Swap pokemon between box and party - sent as BsBOXNUM,BOXSLOT,PARTYSLOT, e.g.Bs0,1,0
						details = message.substring(2).split(",");
						p.swapFromBox(Integer.parseInt(details[0]), 
								Integer.parseInt(details[1]), Integer.parseInt(details[2]));
						break;
					case 'f':
						//Finished with box interfaction
						p.setBoxing(false);
						break;
					}
				}
				break;
			case 'M':
				//Moderation
				if(message.charAt(1) == 'c') {
					p.getTcpSession().write("Cl" + m_players.size() + " players online");
				} else if(p.getAdminLevel() > 0) {
					try {
						switch(message.charAt(1)) {
						case 'a':
							//Server announcement
							for (String s : m_players.keySet()){
								m_players.get(s).getTcpSession().write("q" + message.substring(2));
							}
							break;						
						case 'l':
							//Send an alert
							if(p.getAdminLevel()>1)
								for (String s : m_players.keySet()){
									m_players.get(s).getTcpSession().write("!" + message.substring(2));
								}
							break;
						case 'b':
							//Ban player
							if(m_players.containsKey(message.substring(2))) {
								PlayerChar o = m_players.get(message.substring(2));
								if(m.connect(GameServer.getDatabaseHost(), 
										GameServer.getDatabaseUsername(), 
										GameServer.getDatabasePassword())) {
									m.selectDatabase(GameServer.getDatabaseName());
									m.query("INSERT INTO pn_bans (ip) VALUE ('" + 
											o.getIpAddress()
											+ "')");
									m.close();
								}
							}
							break;
						case 'B':
							//Unban ip
							if(m.connect(GameServer.getDatabaseHost(), 
									GameServer.getDatabaseUsername(), 
									GameServer.getDatabasePassword())) {
								m.selectDatabase(GameServer.getDatabaseName());
								m.query("DELETE FROM pn_bans WHERE ip='" + 
										message.substring(2)
										+ "'");
								m.close();
							}
							break;
						case 'm':
							//Mute player
							if(m_players.containsKey(message.substring(2))) {
								PlayerChar o = m_players.get(message.substring(2));
								o.setMuted(true);
								o.getTcpSession().write("!You have been muted.");
							}
							break;
						case 'u':
							//Unmute player
							if(m_players.containsKey(message.substring(2))) {
								PlayerChar o = m_players.get(message.substring(2));
								o.setMuted(false);
								o.getTcpSession().write("!You have been unmuted.");
							}
							break;
						case 'k':
							if(m_players.containsKey(message.substring(2))) {
								PlayerChar o = m_players.get(message.substring(2));
								o.getTcpSession().write("!You have been kicked from the server.");
								o.getTcpSession().close(true);
							}
							break;
						case 'w':
							//Change weather on current map
							switch(message.charAt(2)) {
							case 'n':
								//Normal
								GameServer.getServiceManager().getTimeService().setForcedWeather(0);
								break;
							case 'r':
								//Rain
								GameServer.getServiceManager().getTimeService().setForcedWeather(1);
								break;
							case 's':
								//Snow/Hail
								GameServer.getServiceManager().getTimeService().setForcedWeather(2);
								break;		
							case 'f':
								//Fog
								GameServer.getServiceManager().getTimeService().setForcedWeather(3);
								break;
							case 'S':
								//Sandstorm
								GameServer.getServiceManager().getTimeService().setForcedWeather(4);
								break;
							case 'R':
								//Random
								GameServer.getServiceManager().getTimeService().setForcedWeather(9);
								break;
							}
						case 's':
							if(p.getAdminLevel() == 2) {
								GameServer.getServiceManager().stop();
								return;
							}
							break;
						case 'n':
							//Announce message to server
							if(p.getAdminLevel() == 2) {
								//TODO: add code?
								  String mes = message.substring(3);
                                  GameServer.getServiceManager().getNetworkService().getChatManager().
                                  queueLocalChatMessage("<SERVER> " + mes, p.getMapX(), p.getMapY(), p.getLanguage());
							}
							break;
						}
					} catch (Exception e) {}
				}
				break;
			case 'b':
				//Battle information
				if(p.isBattling()) {
					BattleTurn turn;
					switch(message.charAt(1)) {
					case 'm':
						//Move selected (bmINDEXOFMOVE)
						turn = BattleTurn.getMoveTurn(Integer.parseInt(message.substring(2)));
						p.getBattleField().queueMove(p.getBattleId(), turn);
						break;
					case 's':
						//Pokemon switch (bsPARTYINDEX)
						int pIndex = Integer.parseInt(message.substring(2));
						if(p.getParty()[pIndex] != null) {
							if(!p.getParty()[pIndex].isFainted()) {
								turn = BattleTurn.getSwitchTurn(pIndex);
								p.getBattleField().queueMove(p.getBattleId(), turn);
							}
						}
						break;
					case 'r':
						//Run
						if(p.getBattleField() instanceof WildBattleField) {
							((WildBattleField) p.getBattleField()).run();
						}
						break;
					}
				}
				break;
			case 'F':
				//Friend list
				String friend = message.substring(2);
				switch(message.charAt(1)) {
				case 'a':
					//Add a friend
					if(m_players.containsKey(friend));
						p.addFriend(message.substring(2));
					break;
				case 'r':
					//Remove a friend
					p.removeFriend(message.substring(2));
					break;
				}
				break;
			case 'I':
				//Use an item, applies inside and outside of battle
				details = message.substring(1).split(",");
				System.out.println("Item used. "+message);
				new Thread(new ItemProcessor(p, details)).start();
				break;
			case 'i':
				//Drop item
				if(p.getBag().removeItem(Integer.parseInt(message.substring(1)), 1)) {
					TcpProtocolHandler.writeMessage(p.getTcpSession(), new ItemMessage(false, 
							Integer.parseInt(message.substring(1)), 1));
				}
				break;
			case 'T':
				//Trade packets
				if(p.isTrading()) {
					switch (message.charAt(1)){
					case 'o':
						//Make an offer ToPOKENUM,MONEYAMOUNT
						details = message.substring(2).split(",");
						p.getTrade().setOffer(p, Integer.parseInt(String.valueOf(details[0])) , 
								Integer.parseInt(String.valueOf(details[1])));
						break;
					case 't':
						//Ready to perform the trade
						p.setTradeAccepted(true);
						break;
					case 'c':
						//Cancel the offer
						p.cancelTradeOffer();
						break;
					case 'C':
						//Cancel the trade
						p.getTrade().endTrade();
						break;
					}
				}
				break;
			case 'C':
				//Chat/Interact
                switch(message.charAt(1)) {
                case 'W':
                	//world chat
                	//TODO finish
                	for (String s : m_players.keySet()){
                		if(p.getTrainingLevel() > 5) {
                			m_players.get(s).getTcpSession().write("z" + "[WORLD]" + p.getName() + ": " + message.substring(2));
                		} else {
                			p.getTcpSession().write("qYou require a trainer level of 5 or greater to send a world message");
                		}
						//System.out.println("WORLD MESSAGE: " + message.substring(2));
                	}
                    break;
                case 'l':
                        //Local chat
                        String mes = message.substring(2);
                        String currentWord;
                        if(!p.isMuted()) {
                        	if(m.connect(GameServer.getDatabaseHost(), 
            						GameServer.getDatabaseUsername(), 
            						GameServer.getDatabasePassword())) {
            					m.selectDatabase(GameServer.getDatabaseName());
            					ResultSet swearWords = m.query("SELECT * FROM forbidden_words");
            					swearWords.beforeFirst();
            					while(swearWords.next()) {
            						currentWord = swearWords.getString("word");
            						if(mes.toLowerCase().contains(currentWord)) {
            							p.getTcpSession().write("qPlease refrain from using offensive language.");
            							break;
            						}
            						} 
    						if(swearWords.isAfterLast()) {
    							int rank = p.getAdminLevel();
    							String rankText = "";
    							switch (rank) {
								case 100:
									rankText = "[Owner]";
									break;
								case 10:
									rankText = "[Donator]";
									break;
								case 11:
									rankText = "[S Donator]";
									break;
								case 80:
									rankText = "[Player Mod]";
									break;
								case 81:
									rankText = "[Game Mod]";
									break;
								case 82:
									rankText = "[Admin]";
									break;
								case 83:
									rankText = "[Head Admin]";
									break;
								case 90:
									rankText = "[Co-Owner]";
									break;
								default:
									break;
								}
    							GameServer.getServiceManager().getNetworkService().getChatManager().queueLocalChatMessage(rankText + " <" + p.getName() + "> " + mes, p.getMapX(), p.getMapY(), p.getLanguage());
    							m.close();
                        	}
							m.close();
                        	}
                        }
                break;
                case 'p':
                        //Private chat
                        details = message.substring(2).split(",");
                        if(m_players.containsKey(details[0])) {
                                GameServer.getServiceManager().getNetworkService().getChatManager().
                                queuePrivateMessage(details[1], m_players.get(details[0]).getTcpSession(), p.getName());
                        }
                        break;
				case 't':
					//Start talking
					if(!p.isTalking() && !p.isBattling())
						p.talkToNpc();
					break;
				case 'f':
					//Finish talking
					if(p.isTalking())
						p.setTalking(false);
					break;
				}
			case 'Z':
				switch(message.charAt(1)) {
				case 'm':
					switch(message.charAt(2)) {
				case 'h':
					name = message.substring(3,4).toUpperCase();
					name = name + message.substring(4);
				if(m_players.containsKey(name)) {
				PlayerChar o = m_players.get(name);
				o.healPokemon();
				o.getTcpSession().write("qYour pokemon have been restored to health by " + p.getName());
				p.getTcpSession().write("qYou healed all of " + name + "'s pokemon.");
				} else {
					p.getTcpSession().write("qCannot find player " + name);
				}
				break;
				case 'g' :
					name = message.substring(3,4).toUpperCase();
					name = name + message.substring(4);
					if(m_players.containsKey(name)) {
						PlayerChar o = m_players.get(name);
						p.setX(o.getX());
						p.setY(o.getY());
						p.setMap(o.getMap(), null);
						p.getTcpSession().write("qTeleporting to " + name + "'s position.");
					}
				break;
				case 'b' :
					name = message.substring(3,4).toUpperCase();
					name = name + message.substring(4);
					if(m_players.containsKey(name)) {
						PlayerChar o = m_players.get(name);
						o.setX(p.getX());
						o.setY(p.getY());
						o.setMap(p.getMap(), null);
						p.getTcpSession().write("qTeleporting " + name + " to your position.");
					} else {
						p.getTcpSession().write("qCan't find player " + name + ".");
					}
				break;
				case 'p' :
					if(p.getAdminLevel() >= 3) {
					String level = message.substring(3,4);
					name = message.substring(5,6).toUpperCase();
					name = name + message.substring(6);
					p.getTcpSession().write("qYou are changing " + name + "'s rights to " + level);
					if(m_players.containsKey(name)) {
						PlayerChar o = m_players.get(name);
						if (level.equalsIgnoreCase("p")) {
							o.setAdminLevel(0);
							o.getTcpSession().write("qYou are now a Player.");
						}
						if (level.equalsIgnoreCase("m")) {
							o.setAdminLevel(1);
							o.getTcpSession().write("qYou are now a Moderator.");
						}
						if (level.equalsIgnoreCase("a")) {
							o.setAdminLevel(2);
							o.getTcpSession().write("qYou are now an Administrator.");
						}
						if (level.equalsIgnoreCase("o")) {
							o.setAdminLevel(3);
							o.getTcpSession().write("qYou are now an Owner.");
							int onlineCount = TcpProtocolHandler.getPlayerCount();
							o.getTcpSession().write("q" + onlineCount);
						}
					}
					}
				break;
				case 'a' :
					AddItem = message.substring(3).split(" ");
					if(m_players.containsKey(AddItem[2])) {
						PlayerChar o = m_players.get(AddItem[2]);	
					o.justAddItem(Integer.parseInt(AddItem[0]), Integer.parseInt(AddItem[1]));
					o.getTcpSession().write("qYou have recieved " + AddItem[1] + " of item " + AddItem[0] + ".");
					p.getTcpSession().write("qYou have given " + AddItem[2] + " " + AddItem[1] + " of item " + AddItem[0] + ".");
					}
				break;
			case 'm' :
				String sprite = message.substring(3);
				p.setSprite(Integer.parseInt(sprite));
				p.updateClientSprite();
				p.getTcpSession().write("qYou are now using Sprite ID " + sprite + ".");
			break;
			case 'r' :
				String ProblemText = message.substring(3);
				long time = System.currentTimeMillis();
				if(m.connect(GameServer.getDatabaseHost(), 
						GameServer.getDatabaseUsername(), 
						GameServer.getDatabasePassword())) {
					m.selectDatabase(GameServer.getDatabaseName());
					m.query("INSERT INTO bug_reports (playerGuid,name,message,createtime,closed) VALUES (" + 
							p.getId() + ",'" + p.getName() + "','" + ProblemText + "'," + time + "," + 0
							+ ")");
					ResultSet onlineStaff = m.query("SELECT username FROM pn_members WHERE adminLevel >=25");
					while(onlineStaff.next()) {
						if(m_players.containsKey(onlineStaff.getString("username"))) {
							PlayerChar o = m_players.get(onlineStaff.getString("username"));
							o.getTcpSession().write("qA new ticket has been created");
						}
					}
					m.close();
					p.getTcpSession().write("qYour ticket has been submitted successfully.");
				} else {
					p.getTcpSession().write("qThere has been an error submitting your ticket. Please try again later.");
				}
			break;
			case 'l' :
				if(m.connect(GameServer.getDatabaseHost(), 
						GameServer.getDatabaseUsername(), 
						GameServer.getDatabasePassword())) {
					m.selectDatabase(GameServer.getDatabaseName());
					ResultSet result = m.query("SELECT * FROM bug_reports WHERE closed=0");
					while(result.next()) {
						name = result.getString("name");
						if(m_players.containsKey(name)) {
						p.getTcpSession().write("qTicket: " + result.getString("id") + ". Created: " + result.getString("createtime") + " by " + result.getString("name"));
						}
						}
					m.close();
				} else {
					p.getTcpSession().write("qCannot retrieve ticket list.");
				}
			break;
			
			case 'v' :
				if(m.connect(GameServer.getDatabaseHost(), 
						GameServer.getDatabaseUsername(), 
						GameServer.getDatabasePassword())) {
					m.selectDatabase(GameServer.getDatabaseName());
					String ticketID = message.substring(3);
					ResultSet result = m.query("SELECT * FROM bug_reports WHERE id=" + ticketID);
					while(result.next()) {
						p.getTcpSession().write("qTicket: " + result.getString("id") + ". Created: " + result.getString("createtime") + " by " + result.getString("name"));
						p.getTcpSession().write("qMessage: " + result.getString("message"));
					}
						m.close();
				} else {
					p.getTcpSession().write("qCannot retrieve ticket.");
				}

			break;
			case 'c' :
				if(m.connect(GameServer.getDatabaseHost(), 
						GameServer.getDatabaseUsername(), 
						GameServer.getDatabasePassword())) {
					m.selectDatabase(GameServer.getDatabaseName());
					String ticketID = message.substring(3);
					m.query("UPDATE bug_reports SET closed=" + p.getId() + " WHERE id=" + ticketID);
					p.getTcpSession().write("qTicket " + ticketID + " has been closed.");
						m.close();
				} else {
					p.getTcpSession().write("qCannot close ticket.");
				}
			break;
					}
				}
			}
		}
	}
	
	/**
	 * When a user disconnects voluntarily, this method is called
	 */
	@Override
	public void sessionClosed(IoSession session) throws Exception {
		/*
		 * Attempt to save the player's data
		 */
		try {
			PlayerChar p = (PlayerChar) session.getAttribute("player");
			if(p != null) {
				if(p.isBattling()) {
					/* If in PvP battle, the player loses */
					if(p.getBattleField() instanceof PvPBattleField) {
						((PvPBattleField) p.getBattleField()).disconnect(p.getBattleId());
					}
					p.setBattleField(null);
					p.setBattling(false);
					p.lostBattle();
				}
				/* If trading, end the trade */
				if(p.isTrading()) {
					p.getTrade().endTrade();
				}
				m_logoutManager.queuePlayer(p);
				GameServer.getServiceManager().getMovementService().removePlayer(p.getName());
				m_players.remove(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Logs out all players and stops login/logout/registration managers
	 */
	public void logoutAll() {
		m_regManager.stop();
		m_loginManager.stop();
		/*
		 * Queue all players to be saved
		 */
		Iterator<PlayerChar> it = m_players.values().iterator();
		PlayerChar p;
		while(it.hasNext()) {
			p = it.next();
			m_logoutManager.queuePlayer(p);
		}
		/*
		 * Since the method is called during a server shutdown, wait for all players to be logged out
		 */
		while(m_logoutManager.getPlayerAmount() > 0);
		m_logoutManager.stop();
	}
	
	/**
	 * Adds a player to the player list
	 * @param p
	 */
	public static void addPlayer(PlayerChar p) {
		synchronized(m_players) {
			m_players.put(p.getName(), p);
			MySqlManager m = new MySqlManager();
			if(m.connect(GameServer.getDatabaseHost(), 
					GameServer.getDatabaseUsername(), 
					GameServer.getDatabasePassword())) {
				m.selectDatabase(GameServer.getDatabaseName());
				ResultSet alertFriends = m.query("SELECT DISTINCT playerName FROM friends_data WHERE friendName='"  + p.getName() + "'");
				
				try {
					while(alertFriends.next()) {
						if(m_players.containsKey(alertFriends.getString("playerName"))) {
						PlayerChar o = m_players.get(alertFriends.getString("playerName"));
						o.getTcpSession().write("q" + p.getName() + " has come online.");
						o.getTcpSession().write("Fo" + p.getName());
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				m.close();
			}

		}
	}
	
	/**
	 * Removes a player from the player list
	 * @param p
	 */
	public static void removePlayer(PlayerChar p) {
		synchronized(m_players) {
			m_players.remove(p.getName());
			MySqlManager m = new MySqlManager();
			if(m.connect(GameServer.getDatabaseHost(), 
					GameServer.getDatabaseUsername(), 
					GameServer.getDatabasePassword())) {
				m.selectDatabase(GameServer.getDatabaseName());
				ResultSet alertFriends = m.query("SELECT DISTINCT playerName FROM friends_data WHERE friendName='"  + p.getName() + "'");
				
				try {
					while(alertFriends.next()) {
						if(m_players.containsKey(alertFriends.getString("playerName"))) {
						PlayerChar o = m_players.get(alertFriends.getString("playerName"));
						o.getTcpSession().write("q" + p.getName() + " has gone offline.");
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				m.close();
			}
		}
	}
	
	/**
	 * Returns a player
	 * @param username
	 * @return
	 */
	public static PlayerChar getPlayer(String username) {
		synchronized(m_players) {
			return m_players.get(username);
		}
	}
	
	/**
	 * Returns true if the player list contains a player
	 * @param username
	 * @return
	 */
	public static boolean containsPlayer(String username) {
		synchronized(m_players) {
			return m_players.containsKey(username);
		}
	}
	
	/**
	 * Kicks idle players
	 */
	public static void kickIdlePlayers() {
		synchronized(m_players) {
			for(PlayerChar p : m_players.values()) {
				if(System.currentTimeMillis() - p.lastPacket >= 900000)
					p.forceLogout();
			}
		}
	}
	
	/**
	 * Returns how many players are logged in
	 * @return
	 */
	public static int getPlayerCount() {
		synchronized(m_players) {
			return m_players.keySet().size();
		}
	}
	
	/**
	 * Writes the message to the session
	 * @param session
	 * @param m
	 */
	public static void writeMessage(IoSession session, PokenetMessage m) {
		if(session.isConnected())
			session.write(m.getMessage());
	}
}
